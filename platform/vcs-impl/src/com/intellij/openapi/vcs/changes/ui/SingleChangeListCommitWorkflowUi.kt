// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

interface SingleChangeListCommitWorkflowUi : DataProvider, Disposable {
  fun activate(): Boolean

  fun addStateListener(listener: CommitWorkflowUiStateListener, parent: Disposable)

  fun addDataProvider(provider: DataProvider)

  fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable)

  fun getChangeList(): LocalChangeList
  fun getIncludedChanges(): List<Change>
  fun getIncludedUnversionedFiles(): List<VirtualFile>

  fun includeIntoCommit(items: Collection<*>)

  fun addInclusionListener(listener: InclusionListener, parent: Disposable)

  fun addChangeListListener(listener: ChangeListListener, parent: Disposable)

  fun getCommitMessage(): String

  fun confirmCommitWithEmptyMessage(): Boolean

  interface ChangeListListener : EventListener {
    fun changeListChanged()
  }
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