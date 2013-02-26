/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputException;
import org.jetbrains.annotations.NotNull;

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
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("dialogs.globalCvsSettings");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGlobalCvsSettingsPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }
}
