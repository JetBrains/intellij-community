// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.XMap
import java.util.*

@State(name = "ActionsLocalSummary", storages = [Storage("actionSummary.xml", roamingType = RoamingType.DISABLED)])
internal class ActionsLocalSummary : SimplePersistentStateComponent<ActionsLocalSummaryState>(ActionsLocalSummaryState()) {
  companion object {
    val instance: ActionsLocalSummary
      get() = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
  }

  fun updateActionsSummary(actionId: String) {
    state.updateActionsSummary(actionId)
  }
}

internal class ActionsLocalSummaryState : BaseState() {
  internal data class ActionSummary(var times: Long = 0, var last: Date = Date())

  @get:XMap
  val data by map<String, ActionSummary>()

  internal fun updateActionsSummary(actionId: String) {
    val actionSummary = data[actionId]
    if (actionSummary == null) {
      data[actionId] = ActionSummary(1, Date())
    }
    else {
      actionSummary.last = Date()
      actionSummary.times++
    }
    incrementModificationCount()
  }
}

private class ActionsLocalSummaryListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    ActionsLocalSummary.instance.updateActionsSummary(event.actionManager.getId(action) ?: action.javaClass.name)
  }
}