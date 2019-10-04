// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.util.containers.forEachLoggingErrors

private val LOG = logger<CommitHandlersNotifier>()

class CommitHandlersNotifier(private val handlers: List<CheckinHandler>) : CommitResultHandler {
  override fun onSuccess(commitMessage: String) = handlers.forEachLoggingErrors(LOG) { it.checkinSuccessful() }

  override fun onCancel() = onFailure(emptyList())

  override fun onFailure(errors: List<VcsException>) = handlers.forEachLoggingErrors(LOG) { it.checkinFailed(errors) }
}