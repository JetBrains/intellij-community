// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tasks.BranchInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class BranchContextTracker implements BranchChangeListener {

  public static final NotificationGroup NOTIFICATION = NotificationGroupManager.getInstance().getNotificationGroup("Branch Context group");

  private final Project myProject;
  /**
   * The files that were open when the checkout started. {@code null} when no checkout is in progress.
   */
  private @Nullable Set<VirtualFile> myFilesOpenBeforeCheckout;

  public BranchContextTracker(@NotNull Project project) {
    myProject = project;
  }

  private WorkingContextManager getContextManager() {
    return WorkingContextManager.getInstance(myProject);
  }

  @Override
  public void branchWillChange(@NotNull String branchName) {
    getContextManager().saveContext(getContextName(branchName), null);
    myFilesOpenBeforeCheckout = Set.copyOf(Arrays.asList(FileEditorManagerEx.getInstanceEx(myProject).getOpenFiles()));
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

  /**
   * Replaces the workspace with the one saved for {@code branchName}.
   * <p>
   * A file that the user opened during the checkout is in no saved workspace.
   * The restore reopens it, and the editor history brings its editor state back.
   * <p>
   * The Rollback action returns the workspace to the state right before this restore.
   * That state includes the files opened during the checkout.
   */
  private void switchContext(@NotNull String branchName, String contextName) {
    WorkingContextManager contextManager = getContextManager();
    var contextBeforeRestore = new Element("context");
    contextManager.saveContext(contextBeforeRestore);

    FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(myProject);
    List<VirtualFile> filesOpenedDuringCheckout = getFilesOpenedDuringCheckout(editorManager);
    VirtualFile selectedFile = editorManager.getCurrentFile();
    myFilesOpenBeforeCheckout = null;

    contextManager.clearContext();
    contextManager.loadContext(contextName);
    for (VirtualFile file : filesOpenedDuringCheckout) {
      if (file.isValid() && !editorManager.isFileOpen(file)) {
        editorManager.openFile(file, file.equals(selectedFile), true);
      }
    }

    Notification notification =
      NOTIFICATION.createNotification(TaskBundle.message("workspace.associated.with.branch.has.been.restored", branchName), NotificationType.INFORMATION);
    notification.addAction(new NotificationAction(TaskBundle.messagePointer("action.Anonymous.text.rollback")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        contextManager.clearContext();
        contextManager.loadContext(contextBeforeRestore);
        notification.expire();
      }
    });
    notification.addAction(new NotificationAction(TaskBundle.messagePointer("action.Anonymous.text.configure.tree.dots")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        new ConfigureBranchContextDialog(myProject).show();
      }
    }).setContextHelpAction(new AnAction(TaskBundle.messagePointer("action.BranchContextTracker.Anonymous.text.what.is.a.workspace"),
                                         TaskBundle.messagePointer("action.BranchContextTracker.Anonymous.description")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {

      }
    }).notify(myProject);
  }

  private @NotNull List<VirtualFile> getFilesOpenedDuringCheckout(@NotNull FileEditorManagerEx editorManager) {
    Set<VirtualFile> filesOpenBeforeCheckout = myFilesOpenBeforeCheckout;
    if (filesOpenBeforeCheckout == null) return List.of();
    return ContainerUtil.filter(editorManager.getOpenFiles(), file -> !filesOpenBeforeCheckout.contains(file));
  }

  private static @NotNull String getContextName(String branchName) {
    return "__branch_context_" + branchName; //NON-NLS
  }

}
