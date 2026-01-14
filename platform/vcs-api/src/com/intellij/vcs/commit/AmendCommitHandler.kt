// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.Hash
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Experimental
interface AmendCommitHandler {
  var commitToAmend: CommitToAmend
  var isAmendCommitModeTogglingEnabled: Boolean
  fun isAmendCommitModeSupported(): Boolean
  fun addAmendCommitModeListener(listener: AmendCommitModeListener, parent: Disposable)
}

interface AmendCommitModeListener : EventListener {
  fun amendCommitModeToggled()
}

sealed interface CommitToAmend {
  object None : CommitToAmend
  object Last : CommitToAmend
  data class Specific(val targetHash: Hash, val targetSubject: @NlsSafe String) : CommitToAmend
}