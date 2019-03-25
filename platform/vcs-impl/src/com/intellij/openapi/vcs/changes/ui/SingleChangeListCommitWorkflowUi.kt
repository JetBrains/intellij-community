// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.TextAccessor
import java.util.*

interface SingleChangeListCommitWorkflowUi : DataProvider, Disposable {
  val commitMessageUi: CommitMessageUi
  val commitOptionsUi: CommitOptionsUi

  var defaultCommitActionName: String

  fun activate(): Boolean
  fun deactivate()

  fun addStateListener(listener: CommitWorkflowUiStateListener, parent: Disposable)

  fun addDataProvider(provider: DataProvider)

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun refreshData()

  fun getChangeList(): LocalChangeList
  fun getDisplayedChanges(): List<Change>
  fun getIncludedChanges(): List<Change>
  fun getDisplayedUnversionedFiles(): List<VirtualFile>
  fun getIncludedUnversionedFiles(): List<VirtualFile>

  fun includeIntoCommit(items: Collection<*>)

  fun addInclusionListener(listener: InclusionListener, parent: Disposable)

  fun addChangeListListener(listener: ChangeListListener, parent: Disposable)

  fun confirmCommitWithEmptyMessage(): Boolean

  fun startBeforeCommitChecks()
  fun endBeforeCommitChecks(result: CheckinHandler.ReturnResult)

  interface ChangeListListener : EventListener {
    fun changeListChanged()
  }
}

//TODO Unify with CommitMessageI
interface CommitMessageUi : TextAccessor {
  override fun getText(): String
  override fun setText(text: String?)

  fun focus()
}

interface CommitOptionsUi {
  fun setOptions(options: CommitOptions)

  fun setVisible(vcses: Collection<AbstractVcs<*>>)
}

interface CommitExecutorListener : EventListener {
  fun executorCalled(executor: CommitExecutor?)
}

interface InclusionListener : EventListener {
  fun inclusionChanged()
}

interface CommitWorkflowUiStateListener : EventListener {
  //  TODO Probably rename to "cancelling"?
  fun cancelled()
}