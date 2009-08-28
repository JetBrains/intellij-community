/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 19:39:53
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NonNls;
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

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("configureIgnoredFilesDialog");
  }

  public static void configure(final Project project) {
    IgnoredSettingsDialog dlg = new IgnoredSettingsDialog(project);
    dlg.show();
    if (!dlg.isOK()) {
      return;
    }
    dlg.myPanel.apply();
    dlg.dispose();
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "IgnoredSettingsDialog";
  }

}