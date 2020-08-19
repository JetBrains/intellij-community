// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

public class ShowHelpPageByIdAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String helpId = Messages.showInputDialog(e.getProject(),
                                             DevKitBundle.message("action.ShowHelpPageById.dialog.enter.help.id"),
                                             DevKitBundle.message("action.ShowHelpPageById.dialog.title"),
                                             null);
    if (helpId != null) {
      HelpManager.getInstance().invokeHelp(helpId);
    }
  }
}
