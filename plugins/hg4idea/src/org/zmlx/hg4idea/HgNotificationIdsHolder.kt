// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea

import com.intellij.notification.impl.NotificationIdsHolder

class HgNotificationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> {
    return listOf(
      BOOKMARK_ERROR,
      BOOKMARK_NAME,
      BRANCH_CREATION_ERROR,
      CHANGESETS_ERROR,
      CLONE_DESTINATION_ERROR,
      CLONE_ERROR,
      COMPARE_WITH_BRANCH_ERROR,
      DEBUGANCESTOR_ERROR,
      EXCEPTION_DURING_MERGE_COMMIT,
      GRAFT_CONTINUE_ERROR,
      GRAFT_ERROR,
      LOG_CMD_EXEC_ERROR,
      MERGE_ERROR,
      MERGE_EXCEPTION,
      MERGE_WARNING,
      MERGE_WITH_ANCESTOR_SKIPPED,
      NOTHING_TO_PUSH,
      PULL_AUTH_REQUIRED,
      PULL_ERROR,
      PUSH_ERROR,
      PUSH_SUCCESS,
      QDELETE_ERROR,
      QFINISH_ERROR,
      QFOLD_ERROR,
      QGOTO_ERROR,
      QIMPORT_ERROR,
      QNEW_ERROR,
      QPOP_COMPLETED_WITH_ERRORS,
      QPOP_ERROR,
      QPUSH_ERROR,
      QREFRESH_ERROR,
      QRENAME_ERROR,
      REBASE_ABORT_ERROR,
      REBASE_CONTINUE_ERROR,
      REBASE_ERROR,
      REMOTE_AUTH_ERROR,
      RENAME_FAILED,
      REPO_CREATION_ERROR,
      REPO_CREATED,
      STATUS_CMD_ERROR,
      TAG_CREATION_ERROR,
      TAG_CREATION_FAILED,
      UNABLE_TO_RUN_EXEC,
      UNSUPPORTED_EXT,
      UNSUPPORTED_VERSION,
      UPDATE_ERROR,
      UPDATE_UNRESOLVED_CONFLICTS_ERROR
    )
  }

  companion object {
    const val BOOKMARK_ERROR = "hg.bookmark.error"
    const val BOOKMARK_NAME = "hg.bookmark.name.is.empty"
    const val BRANCH_CREATION_ERROR = "hg.branch.creation.error"
    const val CHANGESETS_ERROR = "hg4idea.changesets.error"
    const val CLONE_DESTINATION_ERROR = "hg.clone.destination.error"
    const val CLONE_ERROR = "hg.clone.error"
    const val COMPARE_WITH_BRANCH_ERROR = "hg.compare.with.branch.error"
    const val DEBUGANCESTOR_ERROR = "hg.debugancestor.error"
    const val EXCEPTION_DURING_MERGE_COMMIT = "hg.exception.during.merge.commit"
    const val GRAFT_CONTINUE_ERROR = "hg.graft.continue.error"
    const val GRAFT_ERROR = "hg.graft.error"
    const val LOG_CMD_EXEC_ERROR = "hg.log.command.execution.error"
    const val MERGE_ERROR = "hg.merge.error"
    const val MERGE_EXCEPTION = "hg.merge.exception"
    const val MERGE_WARNING = "hg.merge.warning"
    const val MERGE_WITH_ANCESTOR_SKIPPED = "hg.merging.with.ancestor.skipped"
    const val NOTHING_TO_PUSH = "hg.nothing.to.push"
    const val PULL_AUTH_REQUIRED = "hg.pull.auth.required"
    const val PULL_ERROR = "hg.pull.error"
    const val PUSH_ERROR = "hg.push.error"
    const val PUSH_SUCCESS = "hg.pushed.successfully"
    const val QDELETE_ERROR = "hg.qdelete.error"
    const val QFINISH_ERROR = "hg.qfinish.error"
    const val QFOLD_ERROR = "hg.qfold.error"
    const val QGOTO_ERROR = "hg.qgoto.error"
    const val QIMPORT_ERROR = "hg.qimport.error"
    const val QNEW_ERROR = "hg.qnew.error"
    const val QPOP_COMPLETED_WITH_ERRORS = "hg.qpop.completed.with.errors"
    const val QPOP_ERROR = "hg.qpop.error"
    const val QPUSH_ERROR = "hg.qpush.error"
    const val QREFRESH_ERROR = "hg.qrefresh.error"
    const val QRENAME_ERROR = "hg.qrename.error"
    const val REBASE_ABORT_ERROR = "hg.rebase.abort.error"
    const val REBASE_CONTINUE_ERROR = "hg.rebase.continue.error"
    const val REBASE_ERROR = "hg.rebase.error"
    const val REMOTE_AUTH_ERROR = "hg.remote.auth.error"
    const val RENAME_FAILED = "hg.rename.failed"
    const val REPO_CREATION_ERROR = "hg.repo.creation.error"
    const val REPO_CREATED = "hg.repository.created"
    const val STATUS_CMD_ERROR = "hg.status.command.error"
    const val TAG_CREATION_ERROR = "hg.tag.creation.error"
    const val TAG_CREATION_FAILED = "hg.tag.creation.failed"
    const val UNABLE_TO_RUN_EXEC = "hg.unable.to.run.executable"
    const val UNSUPPORTED_EXT = "hg.unsupported.extensions"
    const val UNSUPPORTED_VERSION = "hg.unsupported.version"
    const val UPDATE_ERROR = "hg.update.error"
    const val UPDATE_UNRESOLVED_CONFLICTS_ERROR = "hg.update.unresolved.conflicts.error"
  }
}