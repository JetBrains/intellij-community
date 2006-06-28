package com.intellij.cvsSupport2.ui.experts;

import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.Iterator;

/**
 * author: lesya
 */
public class CvsWizard extends AbstractWizard {
  public CvsWizard(String title, Project project) {
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

  public void updateStep() {
    super.updateStep();
    if (getNumberOfSteps() == 0) return;
    WizardStep currentStep = (WizardStep)getCurrentStepObject();
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
    for (Iterator each = mySteps.iterator(); each.hasNext();) {
      ((WizardStep)each.next()).saveState();
    }
    super.doOKAction();
  }

  public void dispose() {
    try {
      for (Iterator each = mySteps.iterator(); each.hasNext();) {
        ((WizardStep)each.next()).dispose();
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
