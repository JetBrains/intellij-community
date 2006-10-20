package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputException;

import javax.swing.*;

/**
 * author: lesya
 */

public class ConfigureCvsGlobalSettingsDialog extends DialogWrapper {
  private final GlobalCvsSettingsPanel myGlobalCvsSettingsPanel = new GlobalCvsSettingsPanel();

  public ConfigureCvsGlobalSettingsDialog() {
    super(true);
    setTitle(com.intellij.CvsBundle.message("dialog.title.global.cvs.settings"));
    myGlobalCvsSettingsPanel.updateFrom(CvsApplicationLevelConfiguration.getInstance());
    init();
  }

  protected JComponent createCenterPanel() {
    return myGlobalCvsSettingsPanel.getPanel();
  }

  protected void doOKAction() {
    try {
      myGlobalCvsSettingsPanel.saveTo(CvsApplicationLevelConfiguration.getInstance());
    }
    catch (InputException ex) {
      ex.show();
      return;
    }
    super.doOKAction();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("dialogs.globalCvsSettings");
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

}
