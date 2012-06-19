/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;

/**
 * @author yole
 */
public class CacheSettingsDialog extends DialogWrapper {
  private final CacheSettingsPanel myPanel;
  private final Project myProject;

  public CacheSettingsDialog(Project project) {
    super(project, false);
    myProject = project;
    setTitle(VcsBundle.message("cache.settings.dialog.title"));
    myPanel = new CacheSettingsPanel();
    myPanel.initPanel(project);
    myPanel.reset();
    init();
  }
  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  protected void doOKAction() {
    try {
      myPanel.apply();
    }
    catch (ConfigurationException e) {
      //ignore
    }
    super.doOKAction();
  }

  public static boolean showSettingsDialog(final Project project) {
    CacheSettingsDialog dialog = new CacheSettingsDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return false;
    }
    return true;
  }
}
