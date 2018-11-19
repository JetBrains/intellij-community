// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IgnoredSettingsDialog extends DialogWrapper {
  private final IgnoredSettingsPanel myPanel;

  public IgnoredSettingsDialog(Project project) {
    super(project, true);
    setTitle(VcsBundle.message("ignored.configure.title"));
    myPanel = new IgnoredSettingsPanel(project);
    myPanel.reset();
    init();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel.createComponent();
  }

  @Override
  protected String getHelpId() {
    return "configureIgnoredFilesDialog";
  }

  public static void configure(final Project project) {
    IgnoredSettingsDialog dlg = new IgnoredSettingsDialog(project);
    if (!dlg.showAndGet()) {
      return;
    }
    dlg.myPanel.apply();
    dlg.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "IgnoredSettingsDialog";
  }
}