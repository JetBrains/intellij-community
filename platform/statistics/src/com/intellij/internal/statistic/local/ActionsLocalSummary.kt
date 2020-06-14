// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XMap

@State(name = "ActionsLocalSummary", storages = [
  Storage("actionSummary.xml", roamingType = RoamingType.DISABLED),
  Storage("actions_summary.xml", deprecated = true)
], reportStatistic = false)
class ActionsLocalSummary : SimplePersistentStateComponent<ActionsLocalSummaryState>(ActionsLocalSummaryState()) {
  fun getActionsStats(): Map<String, ActionSummary> = state.data.toMap()
}

class ActionSummary {
  @Attribute
  @JvmField
  var times = 0

  @Attribute
  @JvmField
  var last = System.currentTimeMillis()
}

class ActionsLocalSummaryState : BaseState() {
  @get:XMap
  internal val data by map<String, ActionSummary>()

  internal fun updateActionsSummary(actionId: String) {
    val summary = data.getOrPut(actionId) { ActionSummary() }
    summary.last = System.currentTimeMillis()
    summary.times++
    incrementModificationCount()
  }
}

private class ActionsLocalSummaryListener : AnActionListener {
  private val service = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
                        ?: throw ExtensionNotApplicableException.INSTANCE

  override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    if (getPluginInfo(action::class.java).isSafeToReport()) {
      service.state.updateActionsSummary(event.actionManager.getId(action) ?: action.javaClass.name)
    }
  }
}