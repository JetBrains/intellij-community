// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.util.containers.forEachLoggingErrors
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<CheckinHandlersNotifier>()

@ApiStatus.Internal
class CheckinHandlersNotifier(private val committer: Committer,
                              private val handlers: List<CheckinHandler>) : CommitterResultHandler {
  override fun onSuccess() {
    handlers.forEachLoggingErrors(LOG) { it.checkinSuccessful() }
  }

  override fun onCancel() {
    onFailure()
  }

  override fun onFailure() {
    val errors = committer.commitErrors
    handlers.forEachLoggingErrors(LOG) { it.checkinFailed(errors) }
  }
}