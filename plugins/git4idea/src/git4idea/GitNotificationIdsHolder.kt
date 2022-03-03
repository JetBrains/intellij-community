// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.notification.impl.NotificationIdsHolder
import org.jetbrains.annotations.NonNls

class GitNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      BRANCH_CHECKOUT_FAILED,
      BRANCH_CREATION_FAILED,
      BRANCH_DELETED,
      BRANCH_DELETION_ROLLBACK_ERROR,
      BRANCH_OPERATION_ERROR,
      BRANCH_OPERATION_SUCCESS,
      BRANCH_SET_UPSTREAM_ERROR,
      BRANCH_RENAME_ROLLBACK_FAILED,
      BRANCH_RENAME_ROLLBACK_SUCCESS,
      BRANCHES_UPDATE_SUCCESSFUL,
      CANNOT_RESOLVE_CONFLICT,
      CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_ERROR,
      CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_SUCCESSFUL,
      CHECKOUT_ROLLBACK_ERROR,
      CHECKOUT_SUCCESS,
      CHERRY_PICK_ABORT_FAILED,
      CHERRY_PICK_ABORT_SUCCESS,
      CLONE_FAILED,
      CLONE_ERROR_UNABLE_TO_CREATE_DESTINATION_DIR,
      COLLECT_UPDATED_CHANGES_ERROR,
      COMMIT_CANCELLED,
      COMMIT_EDIT_SUCCESS,
      CONFLICT_RESOLVING_ERROR,
      COULD_NOT_COMPARE_WITH_BRANCH,
      COULD_NOT_LOAD_CHANGES_OF_COMMIT,
      COULD_NOT_LOAD_CHANGES_OF_COMMIT_LOG,
      COULD_NOT_SAVE_UNCOMMITTED_CHANGES,
      BRANCH_CREATE_ROLLBACK_SUCCESS,
      BRANCH_CREATE_ROLLBACK_ERROR,
      DELETE_BRANCH_ON_MERGE,
      FETCH_ERROR,
      FETCH_SUCCESS,
      FETCH_CANCELLED,
      FETCH_DETAILS,
      FETCH_RESULT,
      FETCH_RESULT_ERROR,
      FILES_UPDATED_AFTER_MERGE,
      FILES_UP_TO_DATE,
      FIX_TRACKED_NOT_ON_BRANCH,
      INIT_ERROR,
      INIT_FAILED,
      LOCAL_CHANGES_NOT_RESTORED,
      MERGE_ABORT_FAILED,
      MERGE_ABORT_SUCCESS,
      MERGE_ERROR,
      MERGE_FAILED,
      LOCAL_CHANGES_DETECTED,
      MERGE_RESET_ERROR,
      MERGE_ROLLBACK_ERROR,
      PROJECT_UPDATED,
      PROJECT_PARTIALLY_UPDATED,
      PULL_FAILED,
      PUSH_RESULT,
      PUSH_NOT_SUPPORTED,
      REBASE_ABORT_FAILED,
      REBASE_ABORT,
      REBASE_ABORT_SUCCESS,
      REBASE_CANNOT_ABORT,
      REBASE_CANNOT_CONTINUE,
      REBASE_COMMIT_EDIT_UNDO_ERROR,
      REBASE_COMMIT_EDIT_UNDO_ERROR_PROTECTED_BRANCH,
      REBASE_COMMIT_EDIT_UNDO_ERROR_REPO_CHANGES,
      REBASE_NOT_ALLOWED,
      REBASE_NOT_STARTED,
      REBASE_ROLLBACK_FAILED,
      REBASE_SUCCESSFUL,
      REBASE_UPDATE_PROJECT_ERROR,
      REMOTE_BRANCH_DELETION_ERROR,
      REMOTE_BRANCH_DELETION_SUCCESS,
      REPOSITORY_CREATED,
      RESET_FAILED,
      RESET_PARTIALLY_FAILED,
      RESET_SUCCESSFUL,
      REVERT_ABORT_FAILED,
      REVERT_ABORT_SUCCESS,
      STAGE_COMMIT_ERROR,
      STAGE_COMMIT_SUCCESS,
      STASH_FAILED,
      STASH_LOCAL_CHANGES_DETECTED,
      TAG_NOT_CREATED,
      TAG_CREATED,
      TAG_DELETED,
      TAG_DELETION_ROLLBACK_ERROR,
      TAG_REMOTE_DELETION_ERROR,
      TAG_REMOTE_DELETION_SUCCESS,
      TAG_RESTORED,
      UNRESOLVED_CONFLICTS,
      UNSTASH_FAILED,
      UNSTASH_PATCH_APPLIED,
      UNSTASH_WITH_CONFLICTS,
      UNSTASH_UNRESOLVED_CONFLICTS,
      UPDATE_DETACHED_HEAD_ERROR,
      UPDATE_ERROR,
      UPDATE_NO_TRACKED_BRANCH,
      UPDATE_NOTHING_TO_UPDATE
    )
  }

  companion object {
    const val BRANCH_CHECKOUT_FAILED = "git.branch.checkout.failed"
    const val BRANCH_CREATION_FAILED = "git.branch.creation.failed"
    const val BRANCH_DELETED = "git.branch.deleted"
    const val BRANCH_DELETION_ROLLBACK_ERROR = "git.branch.deletion.rollback.error"
    const val BRANCH_OPERATION_ERROR = "git.branch.operation.error"
    const val BRANCH_OPERATION_SUCCESS = "git.branch.operation.success"
    const val BRANCH_SET_UPSTREAM_ERROR = "git.branch.set.upstream.failed"
    const val BRANCH_RENAME_ROLLBACK_FAILED = "git.branch.rename.rollback.failed"
    const val BRANCH_RENAME_ROLLBACK_SUCCESS = "git.branch.rename.rollback.success"
    const val BRANCHES_UPDATE_SUCCESSFUL = "git.branches.update.successful"
    const val CANNOT_RESOLVE_CONFLICT = "git.cannot.resolve.conflict"
    const val CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_ERROR = "git.checkout.new.branch.operation.rollback.error"
    const val CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_SUCCESSFUL =  "git.checkout.new.branch.operation.rollback.successful"
    const val CHECKOUT_ROLLBACK_ERROR = "git.checkout.rollback.error"
    const val CHECKOUT_SUCCESS = "git.checkout.success"
    const val CHERRY_PICK_ABORT_FAILED = "git.cherry.pick.abort.failed"
    const val CHERRY_PICK_ABORT_SUCCESS = "git.cherry.pick.abort.success"
    const val CLONE_FAILED = "git.clone.failed"
    const val CLONE_ERROR_UNABLE_TO_CREATE_DESTINATION_DIR = "git.clone.unable.to.create.destination.dir"
    const val COLLECT_UPDATED_CHANGES_ERROR = "git.rebase.collect.updated.changes.error"
    const val COMMIT_CANCELLED = "git.commit.cancelled"
    const val COMMIT_EDIT_SUCCESS = "git.commit.edit.success"
    const val CONFLICT_RESOLVING_ERROR = "git.conflict.resolving.error"
    const val COULD_NOT_COMPARE_WITH_BRANCH = "git.could.not.compare.with.branch"
    const val COULD_NOT_LOAD_CHANGES_OF_COMMIT = "git.could.not.load.changes.of.commit"
    const val COULD_NOT_LOAD_CHANGES_OF_COMMIT_LOG = "git.log.could.not.load.changes.of.commit"
    const val COULD_NOT_SAVE_UNCOMMITTED_CHANGES = "git.could.not.save.uncommitted.changes"
    const val BRANCH_CREATE_ROLLBACK_SUCCESS = "git.create.branch.rollback.successful"
    const val BRANCH_CREATE_ROLLBACK_ERROR = "git.create.branch.rollback.error"
    const val DELETE_BRANCH_ON_MERGE = "git.delete.branch.on.merge"
    const val FETCH_ERROR = "git.fetch.error"
    const val FETCH_SUCCESS = "git.fetch.success"
    const val FETCH_CANCELLED = "git.fetch.cancelled"
    const val FETCH_DETAILS = "git.fetch.details"
    const val FETCH_RESULT = "git.fetch.result"
    const val FETCH_RESULT_ERROR = "git.fetch.result.error"
    const val FILES_UPDATED_AFTER_MERGE = "git.files.updated.after.merge"
    const val FILES_UP_TO_DATE = "git.all.files.are.up.to.date"
    const val FIX_TRACKED_NOT_ON_BRANCH = "git.fix.tracked.not.on.branch"
    const val INIT_ERROR = "git.init.error"
    const val INIT_FAILED = "git.init.failed"
    const val LOCAL_CHANGES_NOT_RESTORED = "git.local.changes.not.restored"
    const val MERGE_ABORT_FAILED = "git.merge.abort.failed"
    const val MERGE_ABORT_SUCCESS ="git.merge.abort.success"
    const val MERGE_ERROR = "git.merge.error"
    const val MERGE_FAILED = "git.merge.failed"
    const val LOCAL_CHANGES_DETECTED = "git.merge.local.changes.detected"
    const val MERGE_RESET_ERROR = "git.merge.reset.error"
    const val MERGE_ROLLBACK_ERROR = "git.merge.rollback.error"
    const val PROJECT_UPDATED = "git.project.updated"
    const val PROJECT_PARTIALLY_UPDATED = "git.project.partially.updated"
    const val PULL_FAILED = "git.pull.failed"
    const val PUSH_RESULT = "git.push.result"
    const val PUSH_NOT_SUPPORTED = "git.push.not.supported"
    const val REBASE_ABORT_FAILED = "git.rebase.abort.failed"
    const val REBASE_ABORT = "git.rebase.abort"
    const val REBASE_ABORT_SUCCESS = "git.rebase.abort.succeeded"
    const val REBASE_CANNOT_ABORT = "git.rebase.cannot.abort"
    const val REBASE_CANNOT_CONTINUE = "git.rebase.cannot.continue"
    const val REBASE_COMMIT_EDIT_UNDO_ERROR = "git.rebase.commit.edit.undo.error"
    const val REBASE_COMMIT_EDIT_UNDO_ERROR_PROTECTED_BRANCH = "git.rebase.commit.edit.undo.error.protected.branch"
    const val REBASE_COMMIT_EDIT_UNDO_ERROR_REPO_CHANGES = "git.rebase.commit.edit.undo.error.repo.changed"
    const val REBASE_NOT_ALLOWED = "git.rebase.not.allowed"
    const val REBASE_NOT_STARTED = "git.rebase.not.started"
    const val REBASE_ROLLBACK_FAILED = "git.rebase.rollback.failed"
    const val REBASE_SUCCESSFUL = "git.rebase.successful"
    const val REBASE_UPDATE_PROJECT_ERROR = "git.rebase.update.project.error"
    const val REMOTE_BRANCH_DELETION_ERROR = "git.remote.branch.deletion.error"
    const val REMOTE_BRANCH_DELETION_SUCCESS = "git.remote.branch.deletion.success"
    const val REPOSITORY_CREATED = "git.repository.created"
    const val RESET_FAILED = "git.reset.failed"
    const val RESET_PARTIALLY_FAILED = "git.reset.partially.failed"
    const val RESET_SUCCESSFUL = "git.reset.successful"
    const val REVERT_ABORT_FAILED = "git.revert.abort.failed"
    const val REVERT_ABORT_SUCCESS = "git.revert.abort.success"
    const val STAGE_COMMIT_ERROR = "git.stage.commit.error"
    const val STAGE_COMMIT_SUCCESS = "git.stage.commit.successful"
    const val STASH_FAILED = "git.stash.failed"
    const val STASH_LOCAL_CHANGES_DETECTED = "git.stash.local.changes.detected"
    const val TAG_CREATED = "git.tag.created"
    const val TAG_NOT_CREATED = "git.tag.not.created"
    const val TAG_DELETED = "git.tag.deleted"
    const val TAG_DELETION_ROLLBACK_ERROR = "git.tag.deletion.rollback.error"
    const val TAG_REMOTE_DELETION_ERROR = "git.tag.remote.deletion.error"
    const val TAG_REMOTE_DELETION_SUCCESS = "git.tag.remote.deletion.success"
    const val TAG_RESTORED = "git.tag.restored"
    const val UNRESOLVED_CONFLICTS = "git.unresolved.conflicts"
    const val UNSTASH_FAILED = "git.unstash.failed"
    const val UNSTASH_PATCH_APPLIED = "git.unstash.patch.applied"
    const val UNSTASH_WITH_CONFLICTS = "git.unstash.with.conflicts"
    const val UNSTASH_UNRESOLVED_CONFLICTS = "git.unstash.with.unresolved.conflicts"
    const val UPDATE_DETACHED_HEAD_ERROR = "git.update.detached.head.error"
    const val UPDATE_ERROR = "git.update.error"
    const val UPDATE_NO_TRACKED_BRANCH = "git.update.no.tracked.branch.error"
    const val UPDATE_NOTHING_TO_UPDATE = "git.update.nothing.to.update"
  }
}