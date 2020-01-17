// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.*
import kotlin.collections.HashMap

@State(name = "ActionsLocalSummary", storages = [Storage("actions_summary.xml")])
class ActionsLocalSummary : PersistentStateComponent<ActionsLocalSummary> {
  data class ActionSummary(var times: Long = 0, var last: Date = Date())

  var data: MutableMap<String, ActionSummary> = HashMap()
    private set

  fun updateActionsSummary(actionId: String) {
    val actionSummary = data[actionId]
    if (actionSummary == null) {
      data[actionId] = ActionSummary(1, Date())
    }
    else {
      actionSummary.last = Date()
      actionSummary.times++
    }
  }

  override fun getState(): ActionsLocalSummary {
    return this
  }

  override fun loadState(state: ActionsLocalSummary) {
    data = state.data
  }

  companion object {
    val instance: ActionsLocalSummary
      get() = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
  }
}

class ActionsLocalSummaryListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    ActionsLocalSummary.instance.updateActionsSummary(ActionManager.getInstance().getId(action) ?: action.javaClass.name)
  }
}