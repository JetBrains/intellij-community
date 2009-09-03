package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.config.ui.ConfigureCvsGlobalSettingsDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * author: lesya
 */
public class GlobalSettingsAction extends CvsGlobalAction{
  public void actionPerformed(AnActionEvent e) {
    new ConfigureCvsGlobalSettingsDialog().show();
  }
}
