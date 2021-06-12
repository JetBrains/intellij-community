// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUnstashDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Git unstash action
 */
public class GitUnstash extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.message("unstash.action.name");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    AnAction showStashAction = ActionManager.getInstance().getAction("Git.Show.Stash");
    AnActionEvent newEvent = AnActionEvent.createFromDataContext(e.getPlace(),
                                                                 showStashAction.getTemplatePresentation().clone(),
                                                                 e.getDataContext());
    if (ActionUtil.lastUpdateAndCheckDumb(showStashAction, newEvent, true)) {
      ActionUtil.performActionDumbAwareWithCallbacks(showStashAction, newEvent);
    } else {
      super.actionPerformed(e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isFreezedWithNotification(GitBundle.message("unstash.error.can.not.unstash.changes.now"))) return;
    GitUnstashDialog.showUnstashDialog(project, gitRoots, defaultRoot);
  }
}
