/**
 * Copyright 2011 multibit.org
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multibit.viewsystem.swing.action;

import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

import org.multibit.controller.MultiBitController;
import org.multibit.crypto.EncrypterDecrypter;
import org.multibit.crypto.EncrypterDecrypterException;
import org.multibit.file.WalletSaveException;
import org.multibit.message.Message;
import org.multibit.message.MessageManager;
import org.multibit.model.AddressBookData;
import org.multibit.model.MultiBitModel;
import org.multibit.model.PerWalletModelData;
import org.multibit.model.WalletInfo;
import org.multibit.model.WalletMajorVersion;
import org.multibit.utils.ImageLoader;
import org.multibit.viewsystem.swing.view.CreateNewReceivingAddressDialog;
import org.multibit.viewsystem.swing.view.CreateNewReceivingAddressPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.EncryptionType;

/**
 * This {@link Action} represents an action to actually create receiving
 * addresses.
 */
public class CreateNewReceivingAddressSubmitAction extends MultiBitSubmitAction {
    private static Logger log = LoggerFactory.getLogger(CreateNewReceivingAddressAction.class);

    private static final long serialVersionUID = 200152235465875405L;

    private CreateNewReceivingAddressDialog createNewReceivingAddressDialog;
    private CreateNewReceivingAddressPanel createNewReceivingAddressPanel;

    private JPasswordField walletPassword;

    /**
     * Creates a new {@link CreateNewReceivingAddressSubmitAction}.
     */
    public CreateNewReceivingAddressSubmitAction(MultiBitController controller,
            CreateNewReceivingAddressDialog createNewReceivingAddressDialog,
            CreateNewReceivingAddressPanel createNewReceivingAddressPanel, JPasswordField walletPassword) {
        super(controller, "createNewReceivingAddressSubmitAction.text", "createNewReceivingAddressSubmitAction.tooltip",
                "createNewReceivingAddressSubmitAction.mnemonicKey", ImageLoader.createImageIcon(ImageLoader.ADD_ICON_FILE));
        this.createNewReceivingAddressDialog = createNewReceivingAddressDialog;
        this.createNewReceivingAddressPanel = createNewReceivingAddressPanel;
        this.walletPassword = walletPassword;

    }

    /**
     * Create new receiving addresses.
     */
    public void actionPerformed(ActionEvent e) {
        if (abort()) {
            return;
        }

        PerWalletModelData perWalletModelData = controller.getModel().getActivePerWalletModelData();
        boolean encryptNewKeys = false;
        if (controller.getModel().getActiveWallet() != null) {
            if (controller.getModel().getActiveWallet().getEncryptionType() == EncryptionType.ENCRYPTED_SCRYPT_AES && controller.getModel().getActiveWallet().isCurrentlyEncrypted()) {
                if (walletPassword.getPassword() == null || walletPassword.getPassword().length == 0) {
                    // User needs to enter password.
                    createNewReceivingAddressPanel.setMessageText(controller.getLocaliser().getString(
                            "showExportPrivateKeysAction.youMustEnterTheWalletPassword"));
                    return;
                }
                encryptNewKeys = true;

                if (!controller.getModel().getActiveWallet().checkPasswordCanDecryptFirstPrivateKey(walletPassword.getPassword())) {
                    // The password supplied is incorrect.
                    createNewReceivingAddressPanel.setMessageText(controller.getLocaliser().getString(
                            "createNewReceivingAddressSubmitAction.passwordIsIncorrect"));
                    return;
                }
            }
        }

        WalletInfo walletInfo = perWalletModelData.getWalletInfo();
        if (walletInfo == null) {
            walletInfo = new WalletInfo(perWalletModelData.getWalletFilename(), WalletMajorVersion.PROTOBUF_ENCRYPTED);
            perWalletModelData.setWalletInfo(walletInfo);
        }
        
        int numberOfAddressesToCreate = createNewReceivingAddressPanel.getNumberOfAddressesToCreate();

        String walletDescription =  controller.getModel().getActiveWalletWalletInfo().getProperty(WalletInfo.DESCRIPTION_PROPERTY);
        String shortMessage = controller.getLocaliser().getString("createNewReceivingAddressSubmitAction.creatingShort", new Object[] { new Integer(numberOfAddressesToCreate)});
        String longMessage = controller.getLocaliser().getString("createNewReceivingAddressSubmitAction.creatingLong", new Object[] { new Integer(numberOfAddressesToCreate), walletDescription});
        createNewReceivingAddressPanel.setMessageText(shortMessage);
        MessageManager.INSTANCE.addMessage(new Message(" "));
        Message logMessage = new Message(longMessage);
        logMessage.setShowInStatusBar(false);
        MessageManager.INSTANCE.addMessage(logMessage);
        
        createNewReceivingAddressPanel.getCreateNewReceivingAddressSubmitAction().setEnabled(false);
        createNewReceivingAddressPanel.getCancelButton().setEnabled(false);
        createNewReceivingAddressesInBackground(createNewReceivingAddressPanel.getNumberOfAddressesToCreate(), encryptNewKeys, 
                walletPassword.getPassword());
    }
    
    /**
     * Create the new receiving addresses in a background Swing worker thread.
     */
    private void createNewReceivingAddressesInBackground(final int numberOfAddressesToCreate, final boolean encryptNewKeys, 
            final char[] walletPassword) {
        final PerWalletModelData finalPerWalletModelData = controller.getModel().getActivePerWalletModelData();

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            private String shortMessage = null;
            private String longMessage = null;
            private String lastAddressString = null;

            @Override
            protected Boolean doInBackground() throws Exception {
                Boolean successMeasure = Boolean.FALSE;
                
                EncrypterDecrypter walletEncrypterDecrypter = finalPerWalletModelData.getWallet().getEncrypterDecrypter();
                synchronized (controller.getModel().getActiveWallet()) {
                    try {
                        for (int i = 0; i < numberOfAddressesToCreate; i++) {
                            ECKey newKey;
                            if (encryptNewKeys) {
                                // Use the wallet EncrypterDescrypter.
                                newKey = new ECKey(walletEncrypterDecrypter);
                                newKey.encrypt(walletPassword);
                            } else {
                                newKey = new ECKey();
                            }
                            finalPerWalletModelData.getWallet().keychain.add(newKey);
                            lastAddressString = newKey.toAddress(controller.getModel().getNetworkParameters()).toString();
                            finalPerWalletModelData.getWalletInfo().addReceivingAddress(new AddressBookData("", lastAddressString),
                                    false);
                        }
                        successMeasure = Boolean.TRUE;
                    } catch (EncrypterDecrypterException ede) {
                        logError(ede);
                    }
                }
                
                return successMeasure;
            }
            
            private void logError(Exception e) {
                log.error(e.getClass().getName() + " " + e.getMessage());
                e.printStackTrace();
                shortMessage = controller.getLocaliser().getString("createNewReceivingAddressesSubmitAction.failure",
                        new Object[] { e.getMessage() });
                longMessage = shortMessage;
            }

            protected void done() {
                try {
                    Boolean wasSuccessful = get();
 
                    String walletDescription =  controller.getModel().getActiveWalletWalletInfo().getProperty(WalletInfo.DESCRIPTION_PROPERTY);

                    if (wasSuccessful) {
                        shortMessage = controller.getLocaliser().getString("createNewReceivingAddressSubmitAction.createdSuccessfullyShort", new Object[] { new Integer(numberOfAddressesToCreate)});
                        longMessage = controller.getLocaliser().getString("createNewReceivingAddressSubmitAction.createdSuccessfullyLong", new Object[] { new Integer(numberOfAddressesToCreate), walletDescription});

                        log.debug(longMessage);
                        
                        if (createNewReceivingAddressPanel.getReceiveBitcoinPanel() != null) {
                            createNewReceivingAddressPanel.getReceiveBitcoinPanel().getAddressesTableModel().fireTableDataChanged();
                            createNewReceivingAddressPanel.getReceiveBitcoinPanel().selectRows();
                        }
                        controller.getModel().setActiveWalletPreference(MultiBitModel.RECEIVE_ADDRESS, lastAddressString);
                        controller.getModel().setActiveWalletPreference(MultiBitModel.RECEIVE_LABEL, "");

                        try {
                            controller.getFileHandler().savePerWalletModelData(finalPerWalletModelData, false);
                        } catch (WalletSaveException wse) {
                            log.error(wse.getClass().getCanonicalName() + " " + wse.getMessage());
                            MessageManager.INSTANCE.addMessage(new Message(controller.getLocaliser().getString("createNewReceivingAddressesSubmitAction.failure",
                                    new Object[] { wse.getClass().getCanonicalName() + " " + wse.getMessage() })));
                        }
                    } else {
                        log.error(longMessage);
                    }
                    
                    createNewReceivingAddressPanel.getCreateNewReceivingAddressSubmitAction().setEnabled(true);
                    createNewReceivingAddressPanel.getCancelButton().setEnabled(true);

                    if (shortMessage != null) {
                        createNewReceivingAddressPanel.setMessageText(shortMessage);

                         if (createNewReceivingAddressPanel != null && createNewReceivingAddressDialog != null && createNewReceivingAddressDialog.isVisible()) {
                             // Show short message in dialog, long in messages.
                            createNewReceivingAddressPanel.setMessageText(shortMessage);
                            Message logMessage = new Message(longMessage);
                            logMessage.setShowInStatusBar(false);
                            MessageManager.INSTANCE.addMessage(logMessage);
                       } else {
                            // Show long message on statusbar and in messages.
                            MessageManager.INSTANCE.addMessage(new Message(longMessage));
                        }
                    }
                } catch (Exception e) {
                    // Not really used but caught so that SwingWorker shuts down cleanly.
                    log.error(e.getClass() + " " + e.getMessage());
                }
            }
        };
        log.debug("Creating receive addresses in background SwingWorker thread");
        worker.execute();
    }

}