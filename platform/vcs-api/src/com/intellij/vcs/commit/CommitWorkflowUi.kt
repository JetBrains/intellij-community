// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.ui.TextAccessor
import java.util.*

interface CommitWorkflowUi : UiCompatibleDataProvider, Disposable {
  val commitMessageUi: CommitMessageUi

  var defaultCommitActionName: @NlsContexts.Button String

  fun activate(): Boolean

  @Deprecated("Use UiDataRule instead")
  fun addDataProvider(provider: DataProvider)

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun getDisplayedChanges(): List<Change>
  fun getIncludedChanges(): List<Change>
  fun getDisplayedUnversionedFiles(): List<FilePath>
  fun getIncludedUnversionedFiles(): List<FilePath>

  fun addInclusionListener(listener: InclusionListener, parent: Disposable)

  fun startBeforeCommitChecks()
  fun endBeforeCommitChecks(result: CommitChecksResult)
}

//TODO Unify with CommitMessageI
interface CommitMessageUi : TextAccessor {
  override fun getText(): String
  override fun setText(text: String?)

  fun focus()
  fun startLoading()
  fun stopLoading()
}

interface CommitExecutorListener : EventListener {
  fun executorCalled(executor: CommitExecutor?)
}
