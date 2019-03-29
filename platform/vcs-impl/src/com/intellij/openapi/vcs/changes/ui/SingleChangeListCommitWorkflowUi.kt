// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.CommitWorkflowUi
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.ui.TextAccessor
import java.util.*

interface SingleChangeListCommitWorkflowUi : CommitWorkflowUi, DataProvider {
  val commitMessageUi: CommitMessageUi
  val commitOptionsUi: CommitOptionsUi

  var defaultCommitActionName: String

  fun deactivate()

  fun addStateListener(listener: CommitWorkflowUiStateListener, parent: Disposable)

  fun addDataProvider(provider: DataProvider)

  fun refreshData()

  fun getChangeList(): LocalChangeList

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

interface CommitWorkflowUiStateListener : EventListener {
  //  TODO Probably rename to "cancelling"?
  fun cancelled()
}