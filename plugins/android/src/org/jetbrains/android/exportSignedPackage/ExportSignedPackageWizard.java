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

import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ExportSignedPackageWizard extends AbstractWizard<ExportSignedPackageWizardStep> {
  private final Project myProject;

  private AndroidFacet myFacet;
  private KeyStore myKeystore;
  private PrivateKey myPrivateKey;
  private X509Certificate myCertificate;
  private String myKeystoreLocation;
  private char[] myKeystorePassword;
  private List<String> myKeyAliasList;

  public ExportSignedPackageWizard(Project project, List<AndroidFacet> facets) {
    super(AndroidBundle.message("android.export.signed.package.action.text"), project);
    myProject = project;
    assert facets.size() > 0;
    if (facets.size() > 1) {
      addStep(new ChooseModuleStep(this, facets));
    }
    else {
      myFacet = facets.get(0);
    }
    addStep(new KeystoreStep(this));
    addStep(new InitialKeyStep(this));
    addStep(new NewKeyStep(this));
    addStep(new ApkStep(this));
    init();
  }

  @Override
  protected void doOKAction() {
    if (!commitCurrentStep()) return;
    super.doOKAction();
  }

  @Override
  protected void doNextAction() {
    if (!commitCurrentStep()) return;
    super.doNextAction();
  }

  private boolean commitCurrentStep() {
    try {
      mySteps.get(myCurrentStep).commitForNext();
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  protected int getNextStep(int stepIndex) {
    ExportSignedPackageWizardStep step = mySteps.get(stepIndex);
    if (step instanceof KeystoreStep) {
      if (((KeystoreStep)step).isCreateNewKeystore()) {
        // skip InitialKeyStep
        stepIndex++;
      }
    }
    else if (step instanceof InitialKeyStep) {
      if (!((InitialKeyStep)step).isCreateNewKey()) {
        // skip NewKeyStep
        stepIndex++;
      }
    }
    int result = super.getNextStep(stepIndex);
    if (result != myCurrentStep) {
      mySteps.get(result).setPreviousStepIndex(myCurrentStep);
    }
    return result;
  }

  @Override
  protected int getPreviousStep(int stepIndex) {
    ExportSignedPackageWizardStep step = mySteps.get(stepIndex);
    int prevStepIndex = step.getPreviousStepIndex();
    assert prevStepIndex >= 0;
    return prevStepIndex;
  }

  @Override
  protected void updateStep() {
    super.updateStep();
    final int step = getCurrentStep();
    final ExportSignedPackageWizardStep currentStep = mySteps.get(step);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        getRootPane().setDefaultButton(getNextStep(step) != step ? getNextButton() : getFinishButton());

        final JComponent component = currentStep.getPreferredFocusedComponent();
        if (component != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              component.requestFocus();
            }
          });
        }
      }
    });
    getFinishButton().setEnabled(currentStep.canFinish());
  }

  @Override
  protected String getHelpID() {
    ExportSignedPackageWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  public void setKeystore(@NotNull KeyStore keystore) {
    myKeystore = keystore;
  }

  @Nullable
  public KeyStore getKeystore() {
    return myKeystore;
  }

  public Project getProject() {
    return myProject;
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  public void setKeystoreLocation(@NotNull String location) {
    myKeystoreLocation = location;
  }

  public void setKeystorePassword(@NotNull char[] password) {
    myKeystorePassword = password;
  }

  @Nullable
  public String getKeystoreLocation() {
    return myKeystoreLocation;
  }

  @Nullable
  public char[] getKeystorePassword() {
    return myKeystorePassword;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  public void setPrivateKey(@NotNull PrivateKey privateKey) {
    myPrivateKey = privateKey;
  }

  public void setCertificate(@NotNull X509Certificate certificate) {
    myCertificate = certificate;
  }

  public PrivateKey getPrivateKey() {
    return myPrivateKey;
  }

  public X509Certificate getCertificate() {
    return myCertificate;
  }

  public List<String> getKeyAliasList() {
    return myKeyAliasList;
  }

  public void setKeyAliasList(List<String> keyAliasList) {
    myKeyAliasList = keyAliasList;
  }
}
