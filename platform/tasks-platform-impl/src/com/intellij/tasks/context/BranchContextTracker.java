// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.tasks.BranchInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BranchContextTracker implements BranchChangeListener {

  public static final NotificationGroup NOTIFICATION = NotificationGroupManager.getInstance().getNotificationGroup("Branch Context group");

  private final Project myProject;
  private String myLastBranch;

  public BranchContextTracker(@NotNull Project project) {
    myProject = project;
  }

  private WorkingContextManager getContextManager() {
    return WorkingContextManager.getInstance(myProject);
  }

  @Override
  public void branchWillChange(@NotNull String branchName) {
    myLastBranch = branchName;
    getContextManager().saveContext(getContextName(branchName), null);
  }

  @Override
  public void branchHasChanged(@NotNull String branchName) {
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    if (!vcsConfiguration.RELOAD_CONTEXT) return;

    // check if the task is already switched
    TaskManager manager = TaskManager.getManager(myProject);
    if (manager != null) {
      LocalTask task = manager.getActiveTask();
      List<BranchInfo> branches = task.getBranches(false);
      if (branches.stream().anyMatch(info -> branchName.equals(info.name)))
        return;
    }

    String contextName = getContextName(branchName);
    if (!getContextManager().hasContext(contextName)) return;

    TransactionGuard.submitTransaction(myProject, () -> switchContext(branchName, contextName));
  }

  private void switchContext(@NotNull String branchName, String contextName) {
    WorkingContextManager contextManager = getContextManager();
    contextManager.clearContext();
    contextManager.loadContext(contextName);

    Notification notification =
      NOTIFICATION.createNotification(TaskBundle.message("workspace.associated.with.branch.has.been.restored", branchName), NotificationType.INFORMATION);
    if (myLastBranch != null && contextManager.hasContext(getContextName(myLastBranch))) {
      notification.addAction(new NotificationAction(TaskBundle.messagePointer("action.Anonymous.text.rollback")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          contextManager.clearContext();
          contextManager.loadContext(getContextName(myLastBranch));
        }
      });
    }
    notification.addAction(new NotificationAction(TaskBundle.messagePointer("action.Anonymous.text.configure.tree.dots")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        new ConfigureBranchContextDialog(myProject).show();
      }
    }).setContextHelpAction(new AnAction(TaskBundle.messagePointer("action.BranchContextTracker.Anonymous.text.what.is.a.workspace"),
                                         TaskBundle.messagePointer("action.BranchContextTracker.Anonymous.description"), null) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {

      }
    }).notify(myProject);
  }

  @NotNull
  private static String getContextName(String branchName) {
    return "__branch_context_" + branchName; //NON-NLS
  }

}
