// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.Hash
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Experimental
interface AmendCommitHandler {
  val project: Project
  var commitToAmend: CommitToAmend
  val isAmendCommitMode: Boolean
    get() = commitToAmend !is CommitToAmend.None

  var isAmendCommitModeTogglingEnabled: Boolean
  fun isAmendCommitModeSupported(): Boolean
  fun isAmendSpecificCommitSupported(): Boolean
  fun addAmendCommitModeListener(listener: AmendCommitModeListener, parent: Disposable)

  suspend fun getAmendSpecificCommitTargets(): List<CommitToAmend.Resolved> = emptyList()
}

interface AmendCommitModeListener : EventListener {
  fun amendCommitModeToggled()
}

sealed interface CommitToAmend {
  object None : CommitToAmend

  sealed interface Resolved : CommitToAmend {
    val hash: Hash
    val subject: @NlsSafe String
  }

  sealed interface Last : CommitToAmend {
    data class Known(
      override val hash: Hash,
      override val subject: @NlsSafe String
    ) : Resolved, Last
    
    object Unknown : Last
  }
  
  data class Specific(
    override val hash: Hash,
    override val subject: @NlsSafe String
  ) : Resolved
}