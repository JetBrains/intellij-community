/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 18.08.2006
 * Time: 16:35:20
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.actions.AbstractVcsAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;

public class ShowChangesViewAction extends AbstractVcsAction {
  protected void actionPerformed(VcsContext e) {
    if (e.getProject() == null) return;
    final ToolWindowManager manager = ToolWindowManager.getInstance(e.getProject());
    if (manager != null) {
      final ToolWindow window = manager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
      if (window != null) {
        window.show(null);
      }
    }
  }

  protected void update(VcsContext vcsContext, Presentation presentation) {
    presentation.setVisible(getActiveVcses(vcsContext).size() > 0);
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }
}
