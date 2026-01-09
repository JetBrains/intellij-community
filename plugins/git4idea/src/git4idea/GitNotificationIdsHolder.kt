// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.notification.impl.NotificationIdsHolder

class GitNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      APPLY_CHANGES_SUCCESS,
      APPLY_CHANGES_CONFLICTS,
      APPLY_CHANGES_ERROR,
      APPLY_CHANGES_LOCAL_CHANGES_DETECTED,
      BRANCH_UPDATE_FORCE_PUSHED_BRANCH_NOT_ALL_CHERRY_PICKED,
      BRANCH_UPDATE_FORCE_PUSHED_BRANCH_SUCCESS,
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
      CHERRY_PICK_CONTINUE_FAILED,
      CHERRY_PICK_CONTINUE_SUCCESS,
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
      DROP_CHANGES_FAILED,
      FETCH_ERROR,
      FETCH_SUCCESS,
      FETCH_DETAILS,
      FETCH_RESULT,
      FETCH_RESULT_ERROR,
      FILES_UPDATED_AFTER_MERGE,
      FILES_UP_TO_DATE,
      FIX_TRACKED_NOT_ON_BRANCH,
      INIT_ERROR,
      INIT_FAILED,
      INIT_STAGE_FAILED,
      IN_MEMORY_OPERATION_FAILED,
      IN_MEMORY_REBASE_MERGE_CONFLICT,
      IN_MEMORY_REBASE_VALIDATION_FAILED,
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
      STAGE_OPERATION_ERROR,
      STASH_SUCCESSFUL,
      STASH_FAILED,
      STASH_LOCAL_CHANGES_DETECTED,
      STASH_NON_EMPTY_INDEX_DETECTED,
      TAG_CREATED,
      TAG_NOT_CREATED,
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
      UPDATE_NOTHING_TO_UPDATE,
      BAD_EXECUTABLE,
      REBASE_STOPPED_ON_CONFLICTS,
      REBASE_STOPPED_ON_EDITING,
      REBASE_FAILED,
      UNTRACKED_FIES_OVERWITTEN,
      TAGS_LOADING_FAILED,
      OPEN_IN_BROWSER_ERROR,
      IGNORE_FILE_GENERATION_ERROR,
      UNSHALLOW_SUCCESS,
      GPG_AGENT_CONFIGURATION_SUCCESS,
      GPG_AGENT_CONFIGURATION_ERROR,
      GPG_AGENT_CONFIGURATION_PROPOSE,
      GPG_AGENT_CONFIGURATION_PROPOSE_SUGGESTION,
      MODAL_COMMIT_DEPRECATION,
      WORKTREE_ADD_FAILED,
      WORKTREE_COULD_NOT_CREATE_TARGET_DIR,
      WORKING_TREE_DELETED,
      WORKING_TREE_COULD_NOT_DELETE,
    )
  }

  companion object {
    const val APPLY_CHANGES_SUCCESS: String = "git.apply.changes.success"
    const val APPLY_CHANGES_CONFLICTS: String = "git.apply.changes.conflicts"
    const val APPLY_CHANGES_ERROR: String = "git.apply.changes.error"
    const val APPLY_CHANGES_LOCAL_CHANGES_DETECTED: String = "git.apply.changes.local.changes.detected"
    const val BRANCH_UPDATE_FORCE_PUSHED_BRANCH_NOT_ALL_CHERRY_PICKED: String = "git.update.force.pushed.branch.not.all.cherry.picked"
    const val BRANCH_UPDATE_FORCE_PUSHED_BRANCH_SUCCESS: String = "git.update.force.pushed.branch.success"
    const val BRANCH_CHECKOUT_FAILED: String = "git.branch.checkout.failed"
    const val BRANCH_CREATION_FAILED: String = "git.branch.creation.failed"
    const val BRANCH_DELETED: String = "git.branch.deleted"
    const val BRANCH_DELETION_ROLLBACK_ERROR: String = "git.branch.deletion.rollback.error"
    const val BRANCH_OPERATION_ERROR: String = "git.branch.operation.error"
    const val BRANCH_OPERATION_SUCCESS: String = "git.branch.operation.success"
    const val BRANCH_SET_UPSTREAM_ERROR: String = "git.branch.set.upstream.failed"
    const val BRANCH_RENAME_ROLLBACK_FAILED: String = "git.branch.rename.rollback.failed"
    const val BRANCH_RENAME_ROLLBACK_SUCCESS: String = "git.branch.rename.rollback.success"
    const val BRANCHES_UPDATE_SUCCESSFUL: String = "git.branches.update.successful"
    const val CANNOT_RESOLVE_CONFLICT: String = "git.cannot.resolve.conflict"
    const val CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_ERROR: String = "git.checkout.new.branch.operation.rollback.error"
    const val CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_SUCCESSFUL: String = "git.checkout.new.branch.operation.rollback.successful"
    const val CHECKOUT_ROLLBACK_ERROR: String = "git.checkout.rollback.error"
    const val CHECKOUT_SUCCESS: String = "git.checkout.success"
    const val CHERRY_PICK_ABORT_FAILED: String = "git.cherry.pick.abort.failed"
    const val CHERRY_PICK_ABORT_SUCCESS: String = "git.cherry.pick.abort.success"
    const val CHERRY_PICK_CONTINUE_FAILED: String = "git.cherry.pick.continue.failed"
    const val CHERRY_PICK_CONTINUE_SUCCESS: String = "git.cherry.pick.continue.success"
    const val CLONE_FAILED: String = "git.clone.failed"
    const val CLONE_ERROR_UNABLE_TO_CREATE_DESTINATION_DIR: String = "git.clone.unable.to.create.destination.dir"
    const val CLONE_ERROR_UNABLE_TO_FIND_DESTINATION_DIR: String = "git.clone.unable.to.find.destination.dir"
    const val COLLECT_UPDATED_CHANGES_ERROR: String = "git.rebase.collect.updated.changes.error"
    const val COMMIT_CANCELLED: String = "git.commit.cancelled"
    const val COMMIT_EDIT_SUCCESS: String = "git.commit.edit.success"
    const val CONFLICT_RESOLVING_ERROR: String = "git.conflict.resolving.error"
    const val COULD_NOT_COMPARE_WITH_BRANCH: String = "git.could.not.compare.with.branch"
    const val COULD_NOT_LOAD_CHANGES_OF_COMMIT: String = "git.could.not.load.changes.of.commit"
    const val COULD_NOT_LOAD_CHANGES_OF_COMMIT_LOG: String = "git.log.could.not.load.changes.of.commit"
    const val COULD_NOT_SAVE_UNCOMMITTED_CHANGES: String = "git.could.not.save.uncommitted.changes"
    const val BRANCH_CREATE_ROLLBACK_SUCCESS: String = "git.create.branch.rollback.successful"
    const val BRANCH_CREATE_ROLLBACK_ERROR: String = "git.create.branch.rollback.error"
    const val DELETE_BRANCH_ON_MERGE: String = "git.delete.branch.on.merge"
    const val DROP_CHANGES_FAILED: String = "git.rebase.log.changes.drop.failed"
    const val FETCH_ERROR: String = "git.fetch.error"
    const val FETCH_SUCCESS: String = "git.fetch.success"
    const val FETCH_DETAILS: String = "git.fetch.details"
    const val FETCH_RESULT: String = "git.fetch.result"
    const val FETCH_RESULT_ERROR: String = "git.fetch.result.error"
    const val FILES_UPDATED_AFTER_MERGE: String = "git.files.updated.after.merge"
    const val FILES_UP_TO_DATE: String = "git.all.files.are.up.to.date"
    const val FIX_TRACKED_NOT_ON_BRANCH: String = "git.fix.tracked.not.on.branch"
    const val INIT_ERROR: String = "git.init.error"
    const val INIT_FAILED: String = "git.init.failed"
    const val INIT_STAGE_FAILED: String = "git.init.stage.failed"
    const val IN_MEMORY_OPERATION_FAILED: String = "git.in.memory.operation.failed"
    const val IN_MEMORY_REBASE_MERGE_CONFLICT: String = "git.in.memory.rebase.merge.conflict"
    const val IN_MEMORY_REBASE_VALIDATION_FAILED: String = "git.in.memory.rebase.validation.failed"
    const val LOCAL_CHANGES_NOT_RESTORED: String = "git.local.changes.not.restored"
    const val MERGE_ABORT_FAILED: String = "git.merge.abort.failed"
    const val MERGE_ABORT_SUCCESS: String = "git.merge.abort.success"
    const val MERGE_ERROR: String = "git.merge.error"
    const val MERGE_FAILED: String = "git.merge.failed"
    const val LOCAL_CHANGES_DETECTED: String = "git.merge.local.changes.detected"
    const val MERGE_RESET_ERROR: String = "git.merge.reset.error"
    const val MERGE_ROLLBACK_ERROR: String = "git.merge.rollback.error"
    const val PROJECT_UPDATED: String = "git.project.updated"
    const val PROJECT_PARTIALLY_UPDATED: String = "git.project.partially.updated"
    const val PULL_FAILED: String = "git.pull.failed"
    const val PUSH_RESULT: String = "git.push.result"
    const val REBASE_ABORT_FAILED: String = "git.rebase.abort.failed"
    const val REBASE_ABORT: String = "git.rebase.abort"
    const val REBASE_ABORT_SUCCESS: String = "git.rebase.abort.succeeded"
    const val REBASE_CANNOT_ABORT: String = "git.rebase.cannot.abort"
    const val REBASE_CANNOT_CONTINUE: String = "git.rebase.cannot.continue"
    const val REBASE_COMMIT_EDIT_UNDO_ERROR: String = "git.rebase.commit.edit.undo.error"
    const val REBASE_COMMIT_EDIT_UNDO_ERROR_PROTECTED_BRANCH: String = "git.rebase.commit.edit.undo.error.protected.branch"
    const val REBASE_COMMIT_EDIT_UNDO_ERROR_REPO_CHANGES: String = "git.rebase.commit.edit.undo.error.repo.changed"
    const val REBASE_NOT_ALLOWED: String = "git.rebase.not.allowed"
    const val REBASE_NOT_STARTED: String = "git.rebase.not.started"
    const val REBASE_ROLLBACK_FAILED: String = "git.rebase.rollback.failed"
    const val REBASE_SUCCESSFUL: String = "git.rebase.successful"
    const val REBASE_UPDATE_PROJECT_ERROR: String = "git.rebase.update.project.error"
    const val REMOTE_BRANCH_DELETION_ERROR: String = "git.remote.branch.deletion.error"
    const val REMOTE_BRANCH_DELETION_SUCCESS: String = "git.remote.branch.deletion.success"
    const val REPOSITORY_CREATED: String = "git.repository.created"
    const val RESET_FAILED: String = "git.reset.failed"
    const val RESET_PARTIALLY_FAILED: String = "git.reset.partially.failed"
    const val RESET_SUCCESSFUL: String = "git.reset.successful"
    const val REVERT_ABORT_FAILED: String = "git.revert.abort.failed"
    const val REVERT_ABORT_SUCCESS: String = "git.revert.abort.success"
    const val STAGE_COMMIT_ERROR: String = "git.stage.commit.error"
    const val STAGE_COMMIT_SUCCESS: String = "git.stage.commit.successful"
    const val STAGE_OPERATION_ERROR: String = "git.stage.operation.error"
    const val STASH_SUCCESSFUL: String = "git.stash.successful"
    const val STASH_FAILED: String = "git.stash.failed"
    const val STASH_LOCAL_CHANGES_DETECTED: String = "git.stash.local.changes.detected"
    const val STASH_NON_EMPTY_INDEX_DETECTED: String = "git.stash.non.empty.index.detected"
    const val TAG_CREATED: String = "git.tag.created"
    const val TAG_NOT_CREATED: String = "git.tag.not.created"
    const val TAG_DELETED: String = "git.tag.deleted"
    const val TAG_DELETION_ROLLBACK_ERROR: String = "git.tag.deletion.rollback.error"
    const val TAG_REMOTE_DELETION_ERROR: String = "git.tag.remote.deletion.error"
    const val TAG_REMOTE_DELETION_SUCCESS: String = "git.tag.remote.deletion.success"
    const val TAG_RESTORED: String = "git.tag.restored"
    const val UNRESOLVED_CONFLICTS: String = "git.unresolved.conflicts"
    const val UNSTASH_FAILED: String = "git.unstash.failed"
    const val UNSTASH_PATCH_APPLIED: String = "git.unstash.patch.applied"
    const val UNSTASH_WITH_CONFLICTS: String = "git.unstash.with.conflicts"
    const val UNSTASH_UNRESOLVED_CONFLICTS: String = "git.unstash.with.unresolved.conflicts"
    const val UPDATE_DETACHED_HEAD_ERROR: String = "git.update.detached.head.error"
    const val UPDATE_ERROR: String = "git.update.error"
    const val UPDATE_NO_TRACKED_BRANCH: String = "git.update.no.tracked.branch.error"
    const val UPDATE_NOTHING_TO_UPDATE: String = "git.update.nothing.to.update"
    const val BAD_EXECUTABLE: String = "git.bad.executable"
    const val REBASE_STOPPED_ON_CONFLICTS: String = "git.rebase.stopped.due.to.conflicts"
    const val REBASE_STOPPED_ON_EDITING: String = "git.rebase.stopped.for.editing"
    const val REBASE_FAILED: String = "git.rebase.failed"
    const val UNTRACKED_FIES_OVERWITTEN: String = "untracked.files.overwritten"
    const val TAGS_LOADING_FAILED: String = "git.tags.loading.failed"
    const val OPEN_IN_BROWSER_ERROR: String = "git.open.in.browser.error"
    const val IGNORE_FILE_GENERATION_ERROR: String = "git.ignore.file.generation.error"
    const val UNSHALLOW_SUCCESS: String = "git.unshallow.success"
    const val GPG_AGENT_CONFIGURATION_SUCCESS: String = "git.gpg.agent.configuration.success"
    const val GPG_AGENT_CONFIGURATION_ERROR: String = "git.gpg.agent.configuration.error"
    const val GPG_AGENT_CONFIGURATION_PROPOSE: String = "git.gpg.agent.configuration.propose"
    const val GPG_AGENT_CONFIGURATION_PROPOSE_SUGGESTION: String = "git.gpg.agent.configuration.propose.suggestion"
    const val MODAL_COMMIT_DEPRECATION: String = "git.commit.modal.deprecation"
    const val WORKTREE_ADD_FAILED: String = "git.worktree.add.failed"
    const val WORKTREE_COULD_NOT_CREATE_TARGET_DIR: String = "git.worktree.could.not.create.target.dir"
    const val WORKING_TREE_DELETED: String = "git.working.tree.deleted"
    const val WORKING_TREE_COULD_NOT_DELETE: String = "git.working.tree.not.deleted"
  }
}
