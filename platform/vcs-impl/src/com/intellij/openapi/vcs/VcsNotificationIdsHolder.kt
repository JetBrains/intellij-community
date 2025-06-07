// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.notification.impl.NotificationIdsHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VcsNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      EXTERNALLY_ADDED_FILES,
      PROJECT_CONFIGURATION_FILES_ADDED,
      MANAGE_IGNORE_FILES,
      IGNORED_TO_EXCLUDE_NOT_FOUND,
      CHERRY_PICK_ERROR,
      COMMIT_CANCELED,
      COMMIT_FAILED,
      COMMIT_FINISHED,
      COMMIT_FINISHED_INITIAL,
      COMMIT_FINISHED_WITH_WARNINGS,
      COMMIT_CHECKS_FAILED,
      POST_COMMIT_CHECKS_FAILED,
      COMMIT_CHECKS_ONLY_FAILED,
      COMPARE_FAILED,
      COULD_NOT_COMPARE_WITH_BRANCH,
      INACTIVE_RANGES_DAMAGED,
      PATCH_APPLY_ABORTED,
      PATCH_ALREADY_APPLIED,
      PATCH_APPLY_CANNOT_FIND_PATCH_FILE,
      PATCH_APPLY_NEW_FILES_ERROR,
      PATCH_APPLY_NOT_PATCH_FILE,
      PATCH_PARTIALLY_APPLIED,
      PATCH_APPLY_ROLLBACK_FAILED,
      PATCH_APPLY_SUCCESS,
      PATCH_COPIED_TO_CLIPBOARD,
      PATCH_CREATION_FAILED,
      PROJECT_PARTIALLY_UPDATED,
      PROJECT_UPDATE_FINISHED,
      ROOT_ADDED,
      ROOTS_INVALID,
      ROOTS_REGISTERED,
      SHELVE_DELETION_UNDO,
      SHELVE_FAILED,
      SHELVE_SUCCESSFUL,
      SHELF_UNDO_DELETE,
      UNCOMMITTED_CHANGES_SAVING_ERROR,
      CANNOT_LOAD_ANNOTATIONS,
      SUGGESTED_PLUGIN_INSTALL_FAILED,
      OBSOLETE_PLUGIN_UNBUNDLED,
      ADD_UNVERSIONED_ERROR,
      SHARE_CANNOT_FIND_GIT_REPO,
      SHARE_CANNOT_CREATE_REPO,
      SHARE_PROJECT_SUCCESSFULLY_SHARED,
      SHARE_EMPTY_REPO_CREATED,
      SHARE_PROJECT_INIT_COMMIT_FAILED,
      SHARE_PROJECT_INIT_PUSH_FAILED,
      GIT_REPO_INIT_REPO,
    )
  }

  companion object {
    const val EXTERNALLY_ADDED_FILES = "externally.added.files.notification"
    const val PROJECT_CONFIGURATION_FILES_ADDED = "project.configuration.files.added.notification"
    const val MANAGE_IGNORE_FILES = "manage.ignore.files.notification"
    const val IGNORED_TO_EXCLUDE_NOT_FOUND = "ignored.to.exclude.not.found"
    const val CHERRY_PICK_ERROR = "vcs.cherry.pick.error"
    const val COMMIT_CANCELED = "vcs.commit.canceled"
    const val COMMIT_FAILED = "vcs.commit.failed"
    const val COMMIT_FINISHED = "vcs.commit.finished"
    const val COMMIT_FINISHED_INITIAL = "vcs.commit.finished.initial"
    const val COMMIT_FINISHED_WITH_WARNINGS = "vcs.commit.finished.with.warnings"
    const val COMMIT_CHECKS_FAILED = "vcs.commit.checks.failed"
    const val POST_COMMIT_CHECKS_FAILED = "vcs.post.commit.checks.failed"
    const val COMMIT_CHECKS_ONLY_FAILED = "vcs.commit.checks.only.failed"
    const val COMPARE_FAILED = "vcs.compare.failed"
    const val COULD_NOT_COMPARE_WITH_BRANCH = "vcs.could.not.compare.with.branch"
    const val INACTIVE_RANGES_DAMAGED = "vcs.inactive.ranges.damaged"
    const val PATCH_APPLY_ABORTED = "vcs.patch.apply.aborted"
    const val PATCH_ALREADY_APPLIED = "vcs.patch.already.applied"
    const val PATCH_APPLY_CANNOT_FIND_PATCH_FILE = "vcs.patch.apply.cannot.find.patch.file"
    const val PATCH_APPLY_NEW_FILES_ERROR = "vcs.patch.apply.new.files.error"
    const val PATCH_APPLY_NOT_PATCH_FILE = "vcs.patch.apply.not.patch.type.file"
    const val PATCH_PARTIALLY_APPLIED = "vcs.patch.partially.applied"
    const val PATCH_APPLY_ROLLBACK_FAILED = "vcs.patch.apply.rollback.failed"
    const val PATCH_APPLY_SUCCESS = "vcs.patch.apply.success.applied"
    const val PATCH_COPIED_TO_CLIPBOARD = "vcs.patch.copied.to.clipboard"
    const val PATCH_CREATION_FAILED = "vcs.patch.creation.failed"
    const val PROJECT_PARTIALLY_UPDATED = "vcs.project.partially.updated"
    const val PROJECT_UPDATE_FINISHED = "vcs.project.update.finished"
    const val ROOT_ADDED = "vcs.root.added"
    const val ROOTS_INVALID = "vcs.roots.invalid"
    const val ROOTS_REGISTERED = "vcs.roots.registered"
    const val SHELVE_DELETION_UNDO = "vcs.shelve.deletion.undo"
    const val SHELVE_FAILED = "vcs.shelve.failed"
    const val SHELVE_SUCCESSFUL = "vcs.shelve.successful"
    const val SHELF_UNDO_DELETE = "vcs.shelf.undo.delete"
    const val UNCOMMITTED_CHANGES_SAVING_ERROR = "vcs.uncommitted.changes.saving.error"
    const val CANNOT_LOAD_ANNOTATIONS = "vcs.cannot.load.annotations"
    const val SUGGESTED_PLUGIN_INSTALL_FAILED = "vcs.suggested.plugin.install.failed"
    const val OBSOLETE_PLUGIN_UNBUNDLED = "vcs.obsolete.plugin.unbundled"
    const val ADD_UNVERSIONED_ERROR = "vcs.add.unversioned.error"
    const val SHARE_CANNOT_FIND_GIT_REPO = "vcs.share.cannot.find.git.repo"
    const val SHARE_CANNOT_CREATE_REPO = "vcs.share.cannot.create.repo"
    const val SHARE_PROJECT_SUCCESSFULLY_SHARED = "vcs.share.project.successfully.shared"
    const val SHARE_EMPTY_REPO_CREATED = "vcs.share.empty.repo.created"
    const val SHARE_PROJECT_INIT_COMMIT_FAILED = "vcs.share.project.created.init.commit.failed"
    const val SHARE_PROJECT_INIT_PUSH_FAILED = "vcs.share.init.push.failed"
    const val GIT_REPO_INIT_REPO = "vcs.git.repo.init.error"
  }
}

