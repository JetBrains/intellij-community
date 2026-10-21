// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import java.nio.file.Path

/** Every user-facing notification [GitCreateWorkingTreeService] shows while creating a worktree. */
internal object GitWorktreeNotifications {

  fun notifyCouldNotCreateTargetDir(project: Project, message: @NlsSafe @NlsContexts.NotificationContent String) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_COULD_NOT_CREATE_TARGET_DIR,
      GitBundle.message("notification.title.worktree.creation.failed"),
      message,
      true,
    )
  }

  fun notifyWorktreeAddFailed(project: Project, errorHtml: @NlsSafe @NlsContexts.NotificationContent String) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_ADD_FAILED,
      GitBundle.message("notification.title.worktree.creation.failed"),
      errorHtml,
      true,
    )
  }

  fun notifyWorktreeIncludeFileWriteFailed(project: Project, root: VirtualFile) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_INCLUDE_FILE_WRITE_FAILED,
      GitBundle.message("notification.title.worktree.include.file.write.failed"),
      GitBundle.message("notification.content.worktree.include.file.write.failed", root.path),
      true,
    )
  }

  fun notifyConfigCopyFailed(project: Project, failedFiles: List<Path>) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_CONFIG_COPY_FAILED,
      GitBundle.message("notification.title.worktree.config.copy.failed"),
      GitBundle.message("notification.content.worktree.config.copy.failed.files", failedFiles.size, failedFiles.joinToString("\n")),
      true,
    )
  }

  fun notifyConfigCopyFailedUnexpectedly(project: Project, targetWorktreeDir: Path) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_CONFIG_COPY_FAILED,
      GitBundle.message("notification.title.worktree.config.copy.failed"),
      GitBundle.message("notification.content.worktree.config.copy.failed.unexpected", targetWorktreeDir.toString()),
      true,
    )
  }

  fun notifyAdditionalConfigCopyFailed(project: Project, targetWorktreeDir: Path) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_ADDITIONAL_CONFIG_COPY_FAILED,
      GitBundle.message("notification.title.worktree.additional.config.copy.failed"),
      GitBundle.message("notification.content.worktree.additional.config.copy.failed", targetWorktreeDir.toString()),
      true,
    )
  }

  fun notifyAdditionalSettingsFailed(project: Project, targetWorktreeDir: Path) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_ADDITIONAL_SETTINGS_FAILED,
      GitBundle.message("notification.title.worktree.additional.settings.failed"),
      GitBundle.message("notification.content.worktree.additional.settings.failed", targetWorktreeDir.toString()),
      true,
    )
  }

  fun notifyTrustFailed(project: Project, targetWorktreeDir: Path) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_TRUST_FAILED,
      GitBundle.message("notification.title.worktree.trust.failed"),
      GitBundle.message("notification.content.worktree.trust.failed", targetWorktreeDir.toString()),
      true,
    )
  }

  fun notifySetupScriptFailed(project: Project, scriptPath: Path) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_SETUP_SCRIPT_FAILED,
      GitBundle.message("notification.title.worktree.setup.script.failed"),
      GitBundle.message("notification.content.worktree.setup.script.failed", scriptPath.toString()),
      true,
    )
  }

  fun notifyWorktreeCreated(project: Project, branchName: @NlsSafe String, targetWorktreeDir: Path) {
    VcsNotifier.getInstance(project).notifyImportantInfo(
      GitNotificationIdsHolder.WORKTREE_CREATED,
      GitBundle.message("notification.title.worktree.created"),
      GitBundle.message("notification.content.worktree.created", branchName, targetWorktreeDir.toString()),
    )
  }

  fun notifyOpenProjectFailed(project: Project, targetWorktreeDir: Path) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.WORKTREE_OPEN_PROJECT_FAILED,
      GitBundle.message("notification.title.worktree.open.project.failed"),
      GitBundle.message("notification.content.worktree.open.project.failed", targetWorktreeDir.toString()),
      true,
    )
  }
}
