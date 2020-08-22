// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap

@State(name = "ActionsLocalSummary", storages = [Storage("actionSummary.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
class ActionsLocalSummary : PersistentStateComponent<ActionsLocalSummaryState>, SimpleModificationTracker() {
  @Volatile
  private var state = ActionsLocalSummaryState()

  override fun getState() = state

  override fun loadState(state: ActionsLocalSummaryState) {
    this.state = state
  }

  fun getActionsStats(): Map<String, ActionSummary> = if (state.data.isEmpty()) emptyMap() else HashMap(state.data)

  internal fun updateActionsSummary(actionId: String) {
    val summary = state.data.computeIfAbsent(actionId) { ActionSummary() }
    summary.lastUsedTimestamp = System.currentTimeMillis()
    summary.usageCount++
    incModificationCount()
  }
}

@Tag("i")
class ActionSummary {
  @Attribute("c")
  @JvmField
  var usageCount = 0

  @Attribute("l")
  @JvmField
  var lastUsedTimestamp = System.currentTimeMillis()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ActionSummary
    return usageCount == other.usageCount && lastUsedTimestamp == other.lastUsedTimestamp
  }

  override fun hashCode() = (31 * usageCount) + lastUsedTimestamp.hashCode()
}

data class ActionsLocalSummaryState(@get:XMap(entryTagName = "e", keyAttributeName = "n") @get:Property(surroundWithTag = false) internal val data: MutableMap<String, ActionSummary> = HashMap())

private class ActionsLocalSummaryListener : AnActionListener {
  private val service = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
                        ?: throw ExtensionNotApplicableException.INSTANCE

  override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    if (getPluginInfo(action::class.java).isSafeToReport()) {
      service.updateActionsSummary(event.actionManager.getId(action) ?: action.javaClass.name)
    }
  }
}