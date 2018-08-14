// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;

/**
 * @author yole
 */
public class CacheSettingsDialog extends DialogWrapper {
  private final CacheSettingsPanel myPanel;

  public CacheSettingsDialog(Project project) {
    super(project, false);
    setTitle(VcsBundle.message("cache.settings.dialog.title"));
    myPanel = new CacheSettingsPanel();
    myPanel.initPanel(project);
    myPanel.reset();
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  @Override
  protected void doOKAction() {
    myPanel.apply();
    super.doOKAction();
  }

  public static boolean showSettingsDialog(final Project project) {
    CacheSettingsDialog dialog = new CacheSettingsDialog(project);
    if (!dialog.showAndGet()) {
      return false;
    }
    return true;
  }
}
