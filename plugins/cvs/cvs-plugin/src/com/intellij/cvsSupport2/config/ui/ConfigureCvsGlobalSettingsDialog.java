// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputException;

import javax.swing.*;

/**
 * author: lesya
 */
public class ConfigureCvsGlobalSettingsDialog extends DialogWrapper {
  private final GlobalCvsSettingsPanel myGlobalCvsSettingsPanel;

  public ConfigureCvsGlobalSettingsDialog(Project project) {
    super(true);
    setTitle(CvsBundle.message("dialog.title.global.cvs.settings"));
    myGlobalCvsSettingsPanel = new GlobalCvsSettingsPanel(project);
    myGlobalCvsSettingsPanel.updateFrom(CvsApplicationLevelConfiguration.getInstance());
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myGlobalCvsSettingsPanel.getPanel();
  }

  @Override
  protected void doOKAction() {
    try {
      myGlobalCvsSettingsPanel.saveTo(CvsApplicationLevelConfiguration.getInstance());
      super.doOKAction();
    }
    catch (InputException ex) {
      ex.show();
    }
  }

  @Override
  protected String getHelpId() {
    return "dialogs.globalCvsSettings";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGlobalCvsSettingsPanel.getPreferredFocusedComponent();
  }
}