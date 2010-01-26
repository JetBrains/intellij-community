/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui.experts;

import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * author: lesya
 */
public class CvsWizard extends AbstractWizard<WizardStep> {
  protected CvsWizard(String title, Project project) {
    super(title, project);
  }

  protected void init() {
    super.init();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStep();
      }
    });
  }

  protected String getHelpID() {
    return null;
  }

  @Override
  protected void doNextAction() {
    if ((myCurrentStep + 1) >= mySteps.size()) return;
    final WizardStep nextStep = mySteps.get(myCurrentStep + 1);
    if (! nextStep.preNextCheck()) {
      return;
    }

    super.doNextAction();
  }

  public void updateStep() {
    super.updateStep();
    if (getNumberOfSteps() == 0) return;
    WizardStep currentStep = getCurrentStepObject();
    if (!currentStep.setActive()){
      doPreviousAction();
      return;
    }
    currentStep.getPreferredFocusedComponent().requestFocus();
    if (!currentStep.nextIsEnabled()) {
      getNextButton().setEnabled(false);
      getFinishButton().setEnabled(false);
    }
    else {
      getFinishButton().setEnabled((getCurrentStep() + 1) == getNumberOfSteps());
    }

  }

  public void disableNextAndFinish() {
    if (getNextButton().isEnabled() || getFinishButton().isEnabled()) {
      updateStep();
    }
  }

  public void enableNextAndFinish() {
    if ((!getNextButton().isEnabled()) || (!getFinishButton().isEnabled())) {
      updateStep();
    }
  }

  protected void doOKAction() {
    for (final WizardStep step : mySteps) {
      step.saveState();
    }
    super.doOKAction();
  }

  public void dispose() {
    try {
      for (final WizardStep step : mySteps) {
        step.dispose();
      }
    }
    finally {
      super.dispose();
    }
  }

  public void goToPrevious() {
    doPreviousAction();
  }
}
