// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.execution.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import git4idea.branch.GitBranchUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GitBranchesComboBoxAction extends ComboBoxAction {

  public GitBranchesComboBoxAction() {
    getTemplatePresentation().setText(GitBundle.messagePointer("git.show.branches"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    var project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed() || !project.isOpen()) {
      updatePresentation(null, presentation);
    }
    else {
      updatePresentation(project, presentation);
    }
  }

  private static void updatePresentation(
    Project project,
    Presentation presentation
  ) {
    if (project != null) {
      GitRepository repo = GitBranchUtil.getCurrentRepository(project);
      if (repo != null) {
        String branchName = GitBranchUtil.getDisplayableBranchText(repo);
        String name = Executor.shortenNameIfNeeded(branchName);
        presentation.setText(name);
        presentation.setIcon(AllIcons.Vcs.Branch);
      }
      else {
        presentation.setText(ExecutionBundle.messagePointer("action.presentation.GitBranchesComboBoxAction.text"));
        presentation.setIcon(AllIcons.Vcs.Clone);
      }
      presentation.setEnabled(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    super.actionPerformed(e);
  }


  @Override
  protected @NotNull DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup();
  }


  @NotNull
  protected ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>() {
    });
    if (project != null) {
      GitRepository repo = GitBranchUtil.getCurrentRepository(project);
      popup = GitBranchPopup.getInstance(project, repo).asListPopup();
      popup.addListener(new JBPopupListener() {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
        }

        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (disposeCallback != null) {
            disposeCallback.run();
          }
        }
      });
    }
    return popup;
  }
}
