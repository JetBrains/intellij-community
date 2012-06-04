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

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class NewKeyStep extends ExportSignedPackageWizardStep {

  private final ExportSignedPackageWizard myWizard;
  private final NewKeyForm myNewKeyForm;

  public NewKeyStep(ExportSignedPackageWizard wizard) {
    myWizard = wizard;
    myNewKeyForm = new MyNewKeyForm();
  }

  @Override
  public void _init() {
    myNewKeyForm.init();
  }

  @Override
  public JComponent getComponent() {
    return myNewKeyForm.getContentPanel();
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.create.key";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    myNewKeyForm.createKey();
    final KeyStore keyStore = myNewKeyForm.getKeyStore();
    final PrivateKey privateKey = myNewKeyForm.getPrivateKey();
    final X509Certificate certificate = myNewKeyForm.getCertificate();
    assert keyStore != null && privateKey != null && certificate != null;
    myWizard.setKeystore(keyStore);
    myWizard.setPrivateKey(privateKey);
    myWizard.setCertificate(certificate);
  }

  private class MyNewKeyForm extends NewKeyForm {
    @Override
    protected List<String> getExistingKeyAliasList() {
      return myWizard.getKeyAliasList();
    }

    @NotNull
    @Override
    protected String getKeyStoreLocation() {
      final String location = myWizard.getKeystoreLocation();
      assert location != null;
      return location;
    }

    @NotNull
    @Override
    protected char[] getKeyStorePassword() {
      final char[] password = myWizard.getKeystorePassword();
      assert password != null;
      return password;
    }

    @NotNull
    @Override
    protected Project getProject() {
      return myWizard.getProject();
    }
  }
}
