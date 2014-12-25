/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
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
    return myPanel.createComponent();
  }

  @NotNull
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
    if (!dlg.showAndGet()) {
      return;
    }
    dlg.myPanel.apply();
    dlg.dispose();
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "IgnoredSettingsDialog";
  }
}