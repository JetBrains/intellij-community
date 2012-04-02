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

import com.android.jarutils.DebugKeyProvider;
import com.android.jarutils.KeystoreHelper;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class NewKeyStep extends ExportSignedPackageWizardStep {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.android.exportSignedPackage.NewKeyStep");

  private JPanel myContentPanel;
  private JTextField myAliasField;
  private JPasswordField myKeyPasswordField;
  private JPasswordField myConfirmKeyPasswordField;
  private JSpinner myValiditySpinner;
  private JTextField myFirstAndLastNameField;
  private JTextField myOrganizationUnitField;
  private JTextField myCityField;
  private JTextField myStateOrProvinceField;
  private JTextField myCountryCodeField;
  private JPanel myCertificatePanel;
  private JTextField myOrganizationField;

  private final ExportSignedPackageWizard myWizard;

  public NewKeyStep(ExportSignedPackageWizard wizard) {
    myWizard = wizard;
    myValiditySpinner.setModel(new SpinnerNumberModel(25, 1, 1000, 1));
  }

  private int getValidity() {
    SpinnerNumberModel model = (SpinnerNumberModel)myValiditySpinner.getModel();
    return model.getNumber().intValue();
  }

  @Override
  public void _init() {
    myAliasField.setText(generateAlias());
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  private boolean findNonEmptyCertificateField() {
    for (Component component : myCertificatePanel.getComponents()) {
      if (component instanceof JTextField) {
        if (((JTextField)component).getText().trim().length() > 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.create.key";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    if (myAliasField.getText().trim().length() == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.alias.error"));
    }
    checkNewPassword(myKeyPasswordField, myConfirmKeyPasswordField);
    if (!findNonEmptyCertificateField()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.certificate.field.error"));
    }
    createKey();
  }

  @NotNull
  private String generateAlias() {
    List<String> aliasList = myWizard.getKeyAliasList();
    String prefix = "key";
    if (aliasList == null) {
      return prefix + '0';
    }
    Set<String> aliasSet = new HashSet<String>();
    for (String alias : aliasList) {
      aliasSet.add(alias.toLowerCase());
    }
    for (int i = 0; ; i++) {
      String alias = prefix + i;
      if (!aliasSet.contains(alias)) {
        return alias;
      }
    }
  }

  private static void buildDName(StringBuilder builder, String prefix, JTextField textField) {
    if (textField != null) {
      String value = textField.getText().trim();
      if (value.length() > 0) {
        if (builder.length() > 0) {
          builder.append(",");
        }
        builder.append(prefix);
        builder.append('=');
        builder.append(value);
      }
    }
  }

  private String getDName() {
    StringBuilder builder = new StringBuilder();
    buildDName(builder, "CN", myFirstAndLastNameField);
    buildDName(builder, "OU", myOrganizationUnitField);
    buildDName(builder, "O", myOrganizationField);
    buildDName(builder, "L", myCityField);
    buildDName(builder, "ST", myStateOrProvinceField);
    buildDName(builder, "C", myCountryCodeField);
    return builder.toString();
  }

  private void createKey() throws CommitStepException {
    String keystoreLocation = myWizard.getKeystoreLocation();
    assert keystoreLocation != null;
    char[] keystorePassword = myWizard.getKeystorePassword();
    assert keystorePassword != null;
    char[] keyPassword = myKeyPasswordField.getPassword();
    String keyAlias = myAliasField.getText().trim();
    String dname = getDName();
    assert dname != null;
    boolean createdStore = false;
    final StringBuilder errorBuilder = new StringBuilder();
    final StringBuilder outBuilder = new StringBuilder();
    try {
      createdStore = KeystoreHelper
        .createNewStore(keystoreLocation, null, new String(keystorePassword), keyAlias, new String(keyPassword), dname, getValidity(),
                        new DebugKeyProvider.IKeyGenOutput() {
                          public void err(String message) {
                            errorBuilder.append(message).append('\n');
                            LOG.info("Error: " + message);
                          }

                          public void out(String message) {
                            outBuilder.append(message).append('\n');
                            LOG.info(message);
                          }
                        });
    }
    catch (Exception e) {
      LOG.info(e);
      errorBuilder.append(e.getMessage()).append('\n');
    }
    normalizeBuilder(errorBuilder);
    normalizeBuilder(outBuilder);
    try {
      if (createdStore) {
        if (errorBuilder.length() > 0) {
          String prefix = AndroidBundle.message("android.create.new.key.error.prefix");
          Messages.showErrorDialog(myContentPanel, prefix + '\n' + errorBuilder.toString());
        }
      }
      else {
        if (errorBuilder.length() > 0) {
          throw new CommitStepException(errorBuilder.toString());
        }
        if (outBuilder.length() > 0) {
          throw new CommitStepException(outBuilder.toString());
        }
        throw new CommitStepException(AndroidBundle.message("android.cannot.create.new.key.error"));
      }
      PropertiesComponent.getInstance(myWizard.getProject()).setValue(KeystoreStep.DEFAULT_KEYSTORE_LOCATION, keystoreLocation);
      loadKeystoreAndKey(keystoreLocation, keystorePassword, keyAlias, keyPassword);
    }
    finally {
      Arrays.fill(keystorePassword, '\0');
      Arrays.fill(keyPassword, '\0');
    }
  }

  private void loadKeystoreAndKey(String keystoreLocation, char[] keystorePassword, String keyAlias, char[] keyPassword)
    throws CommitStepException {
    FileInputStream fis = null;
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      fis = new FileInputStream(new File(keystoreLocation));
      keyStore.load(fis, keystorePassword);
      myWizard.setKeystore(keyStore);
      KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(keyAlias, new KeyStore.PasswordProtection(keyPassword));
      if (entry == null) {
        throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", keyAlias));
      }
      PrivateKey privateKey = entry.getPrivateKey();
      Certificate certificate = entry.getCertificate();
      if (privateKey == null || certificate == null) {
        throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", keyAlias));
      }
      PropertiesComponent.getInstance(myWizard.getProject()).setValue(InitialKeyStep.DEFAULT_KEY_ALIAS, keyAlias);
      myWizard.setPrivateKey(privateKey);
      myWizard.setCertificate((X509Certificate)certificate);

    }
    catch (Exception e) {
      throw new CommitStepException("Error: " + e.getMessage());
    }
    finally {
      if (fis != null) {
        try {
          fis.close();
        }
        catch (IOException ignored) {
        }
      }
    }
  }

  private static void normalizeBuilder(StringBuilder builder) {
    if (builder.length() > 0) {
      builder.deleteCharAt(builder.length() - 1);
    }
  }
}
