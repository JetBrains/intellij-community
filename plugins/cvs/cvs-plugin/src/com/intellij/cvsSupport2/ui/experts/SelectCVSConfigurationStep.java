package com.intellij.cvsSupport2.ui.experts;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfgurationPanel;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.util.Observable;
import java.util.Observer;

/**
 * author: lesya
 */
public class SelectCVSConfigurationStep extends WizardStep{
  private final SelectCvsConfgurationPanel mySelectCvsConfgurationPanel;
  private final Observer myObserver;


  public SelectCVSConfigurationStep(Project project, CvsWizard wizard) {
    super(com.intellij.CvsBundle.message("dialog.title.select.cvs.configuration"), wizard);
    mySelectCvsConfgurationPanel = new SelectCvsConfgurationPanel(project);
    myObserver = new Observer() {
          public void update(Observable o, Object arg) {
            getWizard().updateStep();
          }
        };
    mySelectCvsConfgurationPanel.getObservable().addObserver(myObserver);
    init();
  }

  protected void dispose() {
    mySelectCvsConfgurationPanel.getObservable().deleteObserver(myObserver);
  }

  public boolean nextIsEnabled() {
    return mySelectCvsConfgurationPanel.getSelectedConfiguration() != null;
  }

  public boolean setActive() {
    return true;
  }

  protected JComponent createComponent() {
    JPanel result = new JPanel(new BorderLayout());
    result.add(mySelectCvsConfgurationPanel, BorderLayout.CENTER);
    JPanel buttonPanel = new JPanel(new BorderLayout());
    result.add(buttonPanel, BorderLayout.SOUTH);
    return result;
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return mySelectCvsConfgurationPanel.getSelectedConfiguration();
  }

  public Component getPreferredFocusedComponent() {
    return mySelectCvsConfgurationPanel.getJList();
  }
}
