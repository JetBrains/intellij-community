// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.execution.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class VCSBranchesComboBoxAction extends ComboBoxAction implements Disposable {
  private Project project;

  public VCSBranchesComboBoxAction() {}

  @Override
  public void update(@NotNull AnActionEvent e) {
    this.project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed() || !project.isOpen()) {
      updatePresentation(null, null, null, presentation, e.getPlace());
    }
    else {
      updatePresentation(ExecutionTargetManager.getActiveTarget(project),
                         RunManager.getInstance(project).getSelectedConfiguration(),
                         project,
                         presentation,
                         e.getPlace());
    }
  }

  private void updatePresentation(ExecutionTarget target,
                                  RunnerAndConfigurationSettings configuration,
                                  Project project,
                                  Presentation presentation,
                                  String place) {
    if (project != null) {
      GitRepository repo = GitBranchUtil.getCurrentRepository(project);
      if (repo != null) {
        String branchName = GitBranchUtil.getDisplayableBranchText(repo);
        String name = Executor.shortenNameIfNeeded(branchName);
        presentation.setText(name);
        presentation.setIcon(AllIcons.Vcs.Branch);
      }
      else {
        presentation.setText(ExecutionBundle.messagePointer("action.presentation.VCSBranchesComboBoxAction.text"));
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

  @Override
  public void dispose() {
    Disposer.dispose(this);
  }

  @NotNull
  protected ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    GitRepository repo = GitBranchUtil.getCurrentRepository(project);
    ListPopup popup = GitBranchPopup.getInstance(project, repo).asListPopup();
    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        disposeCallback.run();
      }
    });
    return popup;
  }
}
