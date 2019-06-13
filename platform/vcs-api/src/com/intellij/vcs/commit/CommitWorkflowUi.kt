// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TextAccessor
import java.util.*

interface CommitWorkflowUi : DataProvider, Disposable {
  val commitMessageUi: CommitMessageUi

  var defaultCommitActionName: String

  fun activate(): Boolean

  fun addDataProvider(provider: DataProvider)

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun refreshData()

  fun getDisplayedChanges(): List<Change>
  fun getIncludedChanges(): List<Change>
  fun getDisplayedUnversionedFiles(): List<VirtualFile>
  fun getIncludedUnversionedFiles(): List<VirtualFile>

  fun includeIntoCommit(items: Collection<*>)

  fun addInclusionListener(listener: InclusionListener, parent: Disposable)

  fun confirmCommitWithEmptyMessage(): Boolean

  fun startBeforeCommitChecks()
  fun endBeforeCommitChecks(result: CheckinHandler.ReturnResult)
}

//TODO Unify with CommitMessageI
interface CommitMessageUi : TextAccessor {
  override fun getText(): String
  override fun setText(text: String?)

  fun focus()
}

interface CommitExecutorListener : EventListener {
  fun executorCalled(executor: CommitExecutor?)
}