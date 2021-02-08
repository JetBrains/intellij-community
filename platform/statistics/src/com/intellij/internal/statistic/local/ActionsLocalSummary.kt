// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap
import kotlin.math.max
import kotlin.math.min

@State(name = "ActionsLocalSummary", storages = [Storage("actionSummary.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
@Service
class ActionsLocalSummary : PersistentStateComponent<ActionsLocalSummaryState>, SimpleModificationTracker() {
  @Volatile
  private var state = ActionsLocalSummaryState()
  @Volatile
  private var totalSummary: ActionsTotalSummary = ActionsTotalSummary()

  override fun getState() = state

  override fun loadState(state: ActionsLocalSummaryState) {
    this.state = state
    this.totalSummary = calculateTotalSummary(state)
  }

  private fun calculateTotalSummary(state: ActionsLocalSummaryState): ActionsTotalSummary {
    var maxUsageCount = 0
    var minUsageCount = Integer.MAX_VALUE
    for (value in state.data.values) {
      maxUsageCount = max(maxUsageCount, value.usageCount)
      minUsageCount = min(minUsageCount, value.usageCount)
    }
    return ActionsTotalSummary(maxUsageCount, minUsageCount)
  }

  fun getTotalStats(): ActionsTotalSummary = totalSummary

  fun getActionsStats(): Map<String, ActionSummary> = if (state.data.isEmpty()) emptyMap() else HashMap(state.data)

  internal fun updateActionsSummary(actionId: String) {
    val summary = state.data.computeIfAbsent(actionId) { ActionSummary() }
    summary.lastUsedTimestamp = System.currentTimeMillis()
    summary.usageCount++

    totalSummary.maxUsageCount = max(summary.usageCount, totalSummary.maxUsageCount)
    totalSummary.minUsageCount = min(summary.usageCount, totalSummary.minUsageCount)
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

data class ActionsTotalSummary(var maxUsageCount: Int = 0, var minUsageCount: Int = 0)

private class ActionsLocalSummaryListener : AnActionListener {
  private val service = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
                        ?: throw ExtensionNotApplicableException.INSTANCE

  override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
    if (getPluginInfo(action::class.java).isSafeToReport()) {
      service.updateActionsSummary(event.actionManager.getId(action) ?: action.javaClass.name)
    }
  }
}