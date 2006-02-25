package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.cvsSupport2.config.ui.CvsRootFieldByFieldConfigurationPanel;
import com.intellij.cvsSupport2.connections.CvsRootDataBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputException;

import javax.swing.*;

/**
 * author: lesya
 */
public class EditCvsConfigurationFieldByFieldDialog extends DialogWrapper {
  private String myConfiguration;
  CvsRootFieldByFieldConfigurationPanel myCvsRootFieldByFieldConfigurationPanel
    = new CvsRootFieldByFieldConfigurationPanel();

  public EditCvsConfigurationFieldByFieldDialog(String config) {
    super(true);
    myConfiguration = config;
    myCvsRootFieldByFieldConfigurationPanel.updateFrom(CvsRootDataBuilder.createSettingsOn(myConfiguration, false));
    setTitle(com.intellij.CvsBundle.message("dialog.title.configure.cvs.root.field.by.field"));
    init();
  }

  protected void doOKAction() {
    try {
      String settings = myCvsRootFieldByFieldConfigurationPanel.getSettings();
      myConfiguration = settings;
      super.doOKAction();
    }
    catch (InputException ex) {
      ex.show();
    }

  }

  protected JComponent createCenterPanel() {
    return myCvsRootFieldByFieldConfigurationPanel.getPanel();
  }

  public String getConfiguration() {
    return myConfiguration;
  }
}
