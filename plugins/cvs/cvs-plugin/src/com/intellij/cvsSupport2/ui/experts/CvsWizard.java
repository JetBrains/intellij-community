/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * author: lesya
 */
public class CvsWizard extends AbstractWizard<WizardStep> {

  protected CvsWizard(String title, Project project) {
    super(title, project);
  }

  @Override
  protected String getHelpID() {
    return null;
  }

  @Override
  protected void doNextAction() {
    if (!isLastStep()) {
      final WizardStep nextStep = mySteps.get(getNextStep());
      if (!nextStep.preNextCheck()) {
        return;
      }
      nextStep.activate();
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
  }

  @Override
  public void updateButtons() {
    super.updateButtons();
  }

  @Override
  protected boolean canGoNext() {
    final int numberOfSteps = getNumberOfSteps();
    if (numberOfSteps == 0) return false;

    final WizardStep currentStep = getCurrentStepObject();
    return currentStep.nextIsEnabled();
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
