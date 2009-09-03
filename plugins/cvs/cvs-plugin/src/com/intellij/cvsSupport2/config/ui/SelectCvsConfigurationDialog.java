package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfgurationPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.util.Observable;
import java.util.Observer;

/**
 * author: lesya
 */
public class SelectCvsConfigurationDialog extends DialogWrapper {
  private final SelectCvsConfgurationPanel myPanel;

  public SelectCvsConfigurationDialog(Project project) {
    super(true);
    myPanel = new SelectCvsConfgurationPanel(project);
    setOKActionEnabled(myPanel.getSelectedConfiguration() != null);

    myPanel.getObservable().addObserver(new Observer() {
      public void update(Observable o, Object arg) {
        setOKActionEnabled(myPanel.getSelectedConfiguration() != null);
      }
    });
    setTitle(com.intellij.CvsBundle.message("dialog.title.select.cvs.root.configuration"));

    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return (JComponent)myPanel.getJList();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return myPanel.getSelectedConfiguration();
  }


}
