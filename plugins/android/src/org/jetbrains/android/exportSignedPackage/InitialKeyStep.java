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
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class InitialKeyStep extends ExportSignedPackageWizardStep {
  public static final String DEFAULT_KEY_ALIAS = "KeyAlias";

  private JRadioButton myNewKeyButton;
  private JPanel myContentPanel;
  private JRadioButton myExistingKeyButton;
  private JComboBox myAliasCombo;
  private JPasswordField myKeyPasswordField;
  private JPanel myKeyOptionsPanel;
  private List<String> myAliasList;
  private boolean myInited;

  private final ExportSignedPackageWizard myWizard;

  public InitialKeyStep(ExportSignedPackageWizard wizard) {
    myWizard = wizard;
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean enabled = myExistingKeyButton.isSelected();
        UIUtil.setEnabled(myKeyOptionsPanel, enabled, true);
      }
    };
    myNewKeyButton.addActionListener(listener);
    myExistingKeyButton.addActionListener(listener);
    myExistingKeyButton.setSelected(true);
  }

  @Override
  public void _init() {
    if (myInited) return;
    KeyStore keystore = myWizard.getKeystore();
    assert keystore != null;
    myAliasList = new ArrayList<String>();
    try {
      for (Enumeration<String> aliases = keystore.aliases(); aliases.hasMoreElements();) {
        myAliasList.add(aliases.nextElement());
      }
    }
    catch (KeyStoreException e) {
      Messages.showErrorDialog(myContentPanel, e.getMessage(), AndroidBundle.message("android.export.package.keystore.error.title"));
      return;
    }
    if (myAliasList.size() > 0) {
      myExistingKeyButton.setSelected(true);
    }
    else {
      myNewKeyButton.setSelected(true);
    }
    myAliasCombo.setModel(new CollectionComboBoxModel(myAliasList, myAliasList.size() > 0 ? myAliasList.get(0) : null));
    String defaultAlias = PropertiesComponent.getInstance().getValue(DEFAULT_KEY_ALIAS);
    if (defaultAlias != null) {
      myAliasCombo.setSelectedItem(defaultAlias);
    }
    myInited = true;
  }

  private void loadKey(String alias, char[] password) throws CommitStepException {
    KeyStore.PrivateKeyEntry entry;
    try {
      KeyStore keyStore = myWizard.getKeystore();
      assert keyStore != null;
      entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias, new KeyStore.PasswordProtection(password));
    }
    catch (Exception e) {
      throw new CommitStepException("Error: " + e.getMessage());
    }
    if (entry == null) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", alias));
    }
    PrivateKey privateKey = entry.getPrivateKey();
    Certificate certificate = entry.getCertificate();
    if (privateKey == null || certificate == null) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", alias));
    }
    PropertiesComponent.getInstance().setValue(DEFAULT_KEY_ALIAS, alias);
    myWizard.setPrivateKey(privateKey);
    myWizard.setCertificate((X509Certificate)certificate);
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.specify.key";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    myWizard.setKeyAliasList(myAliasList);
    char[] password = myKeyPasswordField.getPassword();
    try {
      if (myExistingKeyButton.isSelected()) {
        if (myAliasCombo.getSelectedItem() == null) {
          throw new CommitStepException(AndroidBundle.message("android.extract.package.select.key.alias.error"));
        }
        checkPassword(password);
        String alias = (String)myAliasCombo.getSelectedItem();
        loadKey(alias, password);
      }
    }
    finally {
      Arrays.fill(password, '\0');
    }
  }

  @Override
  protected JComponent getPreferredFocusedComponent() {
    if (myExistingKeyButton.isSelected()) {
      if (myAliasCombo.getSelectedItem() != null) {
        return myKeyPasswordField;
      }
    }
    return null;
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  public boolean isCreateNewKey() {
    return myNewKeyButton.isSelected();
  }
}
