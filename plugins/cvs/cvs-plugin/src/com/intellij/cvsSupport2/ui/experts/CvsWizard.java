/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;

/**
 * author: lesya
 */
public class CvsWizard extends AbstractWizard<WizardStep> {
  protected CvsWizard(String title, Project project) {
    super(title, project);
  }

  @Override
  protected void init() {
    super.init();
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        final boolean isLastStep = (getCurrentStep() + 1) == getNumberOfSteps();
        final JButton defaultButton;
        if (SystemInfo.isMac) {
          defaultButton = getNextButton();
        } else {
          defaultButton = isLastStep ? getFinishButton() : getNextButton();
        }
        getRootPane().setDefaultButton(defaultButton);
        updateStep();
      }
    });
  }

  @Override
  protected String getHelpID() {
    return null;
  }

  @Override
  protected void doNextAction() {
    final boolean lastStep = isLastStep();
    if (!lastStep) {
      final WizardStep nextStep = mySteps.get(getNextStep());
      if (!nextStep.preNextCheck()) {
        return;
      }
      nextStep.focus();
    }
    super.doNextAction();
  }

  @Override
  protected void doPreviousAction() {
    mySteps.get(getPreviousStep()).focus();
    super.doPreviousAction();
  }

  @Override
  public void updateStep() {
    super.updateStep();
    final int numberOfSteps = getNumberOfSteps();
    if (numberOfSteps == 0) return;
    final WizardStep currentStep = getCurrentStepObject();
    if (!currentStep.setActive()){
      doPreviousAction();
      return;
    }
    if (!currentStep.nextIsEnabled()) {
      getNextButton().setEnabled(false);
      getFinishButton().setEnabled(false);
    }
    else {
      final boolean enableFinish = (getCurrentStep() + 1) == numberOfSteps;
      getFinishButton().setEnabled(enableFinish);
      getNextButton().setEnabled(!enableFinish);
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

  @Override
  protected void doOKAction() {
    for (final WizardStep step : mySteps) {
      step.saveState();
    }
    super.doOKAction();
  }

  @Override
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
}
