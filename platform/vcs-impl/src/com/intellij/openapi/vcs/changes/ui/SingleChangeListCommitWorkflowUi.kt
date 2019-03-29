// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.CommitWorkflowUi
import com.intellij.openapi.vcs.changes.LocalChangeList
import java.util.*

interface SingleChangeListCommitWorkflowUi : CommitWorkflowUi, DataProvider {
  val commitOptionsUi: CommitOptionsUi

  fun deactivate()

  fun addStateListener(listener: CommitWorkflowUiStateListener, parent: Disposable)

  fun addDataProvider(provider: DataProvider)

  fun refreshData()

  fun getChangeList(): LocalChangeList

  fun addChangeListListener(listener: ChangeListListener, parent: Disposable)

  interface ChangeListListener : EventListener {
    fun changeListChanged()
  }
}

interface CommitOptionsUi {
  fun setOptions(options: CommitOptions)

  fun setVisible(vcses: Collection<AbstractVcs<*>>)
}

interface CommitWorkflowUiStateListener : EventListener {
  //  TODO Probably rename to "cancelling"?
  fun cancelled()
}