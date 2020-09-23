// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.branch.DvcsBranchUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import git4idea.branch.GitBranchUtil;

import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class GitBranchesComboBoxAction extends ComboBoxAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    var project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed() || !project.isOpen()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    GitRepository repo = GitBranchUtil.getCurrentRepository(project);
    if (repo == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    String branchName = GitBranchUtil.getDisplayableBranchText(repo);
    String name = DvcsBranchUtil.shortenBranchName(branchName);
    presentation.setText(name);
    presentation.setIcon(AllIcons.Vcs.Branch);
    presentation.setEnabledAndVisible(true);
  }

  @Override
  protected @NotNull DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup();
  }

  @Override
  protected @NotNull ListPopup createActionPopup(@NotNull DataContext context,
                                                 @NotNull JComponent component,
                                                 @Nullable Runnable disposeCallback) {
    Project project = Objects.requireNonNull(context.getData(CommonDataKeys.PROJECT));
    GitRepository repo = Objects.requireNonNull(GitBranchUtil.getCurrentRepository(project));

    ListPopup popup = GitBranchPopup.getInstance(project, repo).asListPopup();
    popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        if (disposeCallback != null) {
          disposeCallback.run();
        }
      }
    });
    return popup;
  }
}
