/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.exportSignedPackage;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * @author Eugene.Kudelevsky
 */
class KeystoreStep extends ExportSignedPackageWizardStep {
  public static final String DEFAULT_KEYSTORE_LOCATION = "KeystoreLocation";

  private JPanel myContentPanel;
  private JRadioButton myExistingKeystoreButton;
  private JRadioButton myNewKeystoreButton;
  private TextFieldWithBrowseButton myKeystoreLocationField;
  private JPasswordField myKeystorePasswordField;
  private JPasswordField myConfirmKeystorePasswordField;
  private JLabel myConfirmKeystorePasswordLabel;
  private JLabel myKeystoreLocationLabel;

  private final ExportSignedPackageWizard myWizard;

  public KeystoreStep(ExportSignedPackageWizard wizard) {
    myWizard = wizard;
    myKeystoreLocationLabel.setLabelFor(myKeystoreLocationField);
    final String defaultLocation = PropertiesComponent.getInstance().getValue(DEFAULT_KEYSTORE_LOCATION);
    final SaveFileListener newKeystoreLocationListener = new SaveFileListener(myContentPanel, myKeystoreLocationField,
                                                                              AndroidBundle.message(
                                                                                "android.extract.package.choose.keystore.title")) {
      @Override
      protected String getDefaultLocation() {
        return defaultLocation;
      }
    };
    final ExistingKeystoreLocationListener existingKeystoreLocationListener = new ExistingKeystoreLocationListener();
    if (defaultLocation != null) {
      myKeystoreLocationField.setText(defaultLocation);
      myExistingKeystoreButton.setSelected(true);
      myKeystoreLocationField.getButton().addActionListener(existingKeystoreLocationListener);
      myConfirmKeystorePasswordField.setEnabled(false);
    }
    else {
      myNewKeystoreButton.setSelected(true);
      myKeystoreLocationField.getButton().addActionListener(newKeystoreLocationListener);
    }
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean newKeystore = myNewKeystoreButton.isSelected();
        myConfirmKeystorePasswordLabel.setEnabled(newKeystore);
        myConfirmKeystorePasswordField.setEnabled(newKeystore);
        if (newKeystore) {
          myKeystoreLocationField.getButton().removeActionListener(existingKeystoreLocationListener);
          myKeystoreLocationField.getButton().addActionListener(newKeystoreLocationListener);
        }
        else {
          myKeystoreLocationField.getButton().removeActionListener(newKeystoreLocationListener);
          myKeystoreLocationField.getButton().addActionListener(existingKeystoreLocationListener);
        }
      }
    };
    myExistingKeystoreButton.addActionListener(listener);
    myNewKeystoreButton.addActionListener(listener);
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  @Nullable
  private KeyStore checkExistingKeystoreOptionsAndCreateKeystore(File keystoreFile) throws CommitStepException {
    char[] password = myKeystorePasswordField.getPassword();
    FileInputStream fis = null;
    checkPassword(password);
    if (!keystoreFile.isFile()) {
      throw new CommitStepException(AndroidBundle.message("android.cannot.find.file.error", keystoreFile.getPath()));
    }
    KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      fis = new FileInputStream(keystoreFile);
      keyStore.load(fis, password);
    }
    catch (Exception e) {
      throw new CommitStepException(e.getMessage());
    }
    finally {
      if (fis != null) {
        try {
          fis.close();
        }
        catch (IOException ignored) {
        }
      }
      Arrays.fill(password, '\0');
    }
    return keyStore;
  }

  private void checkNewKeystoreOptions(File keystoreFile) throws CommitStepException {
    checkNewPassword(myKeystorePasswordField, myConfirmKeystorePasswordField);
    File parentFile = keystoreFile.getParentFile();
    if (parentFile == null || !parentFile.isDirectory()) {
      String parentDir = keystoreFile.getParent();
      if (parentDir != null) {
        throw new CommitStepException(AndroidBundle.message("android.cannot.find.directory.error", parentDir));
      }
      else {
        throw new CommitStepException(AndroidBundle.message("android.cannot.find.parent.directory.error", keystoreFile.getName()));
      }
    }
  }

  public boolean isCreateNewKeystore() {
    return myNewKeystoreButton.isSelected();
  }

  @Override
  protected JComponent getPreferredFocusedComponent() {
    if (myExistingKeystoreButton.isSelected()) {
      final String text = myKeystoreLocationField.getText();
      if (text != null && text.length() > 0) {
        return myKeystorePasswordField;
      }
    }
    return null;
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.specify.keystore";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    String keyStoreLocation = myKeystoreLocationField.getText().trim();
    if (keyStoreLocation.length() == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.keystore.location.error"));
    }
    File file = new File(keyStoreLocation);
    if (myNewKeystoreButton.isSelected()) {
      checkNewKeystoreOptions(file);
    }
    else {
      KeyStore keyStore = checkExistingKeystoreOptionsAndCreateKeystore(file);
      if (keyStore != null) {
        myWizard.setKeystore(keyStore);
      }
      else {
        throw new CommitStepException(AndroidBundle.message("android.export.package.keystore.error.title"));
      }
      PropertiesComponent.getInstance().setValue(DEFAULT_KEYSTORE_LOCATION, keyStoreLocation);
    }
    myWizard.setKeystoreLocation(keyStoreLocation);
    myWizard.setKeystorePassword(myKeystorePasswordField.getPassword());
  }

  private class ExistingKeystoreLocationListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      String path = myKeystoreLocationField.getText().trim();
      if (path == null || path.length() == 0) {
        String defaultLocation = PropertiesComponent.getInstance().getValue(DEFAULT_KEYSTORE_LOCATION);
        path = defaultLocation != null ? defaultLocation : "";
      }
      VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path);
      VirtualFile[] files = FileChooser.chooseFiles(myContentPanel, new FileChooserDescriptor(true, false, false, false, false, false), f);
      if (files.length > 0) {
        assert files.length == 1;
        myKeystoreLocationField.setText(FileUtil.toSystemDependentName(files[0].getPath()));
      }
    }
  }
}
