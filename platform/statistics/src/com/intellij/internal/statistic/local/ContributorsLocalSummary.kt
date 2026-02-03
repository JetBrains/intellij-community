// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local

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

@State(name = "ContributorsLocalSummary", storages = [Storage("contributorSummary.xml", roamingType = RoamingType.DISABLED)],
       reportStatistic = false)
@Service
class ContributorsLocalSummary : PersistentStateComponent<ContributorsLocalSummaryState>, SimpleModificationTracker() {
  companion object {
    @JvmStatic
    fun getInstance() = ApplicationManager.getApplication().getService(ContributorsLocalSummary::class.java)
                        ?: throw ExtensionNotApplicableException.create()
  }
  @Volatile
  private var state = ContributorsLocalSummaryState()

  @Volatile
  private var selectionsRange: ContributorsSelectionsRange = ContributorsSelectionsRange(0, Integer.MAX_VALUE,
                                                                                         0, Integer.MAX_VALUE)

  override fun getState() = state

  override fun loadState(state: ContributorsLocalSummaryState) {
    this.state = state
    this.selectionsRange = calculateSelectionsRange(state)
  }

  private fun calculateSelectionsRange(state: ContributorsLocalSummaryState): ContributorsSelectionsRange {
    val maxAllTabSelectionCount = state.data.values.map { it.allTabSelectionCount }.maxOrNull() ?: 0
    val minAllTabSelectionCount = state.data.values.map { it.allTabSelectionCount }.minOrNull() ?: Integer.MAX_VALUE

    val maxOtherTabsSelectionCount = state.data.values.map { it.otherTabsSelectionCount }.maxOrNull() ?: 0
    val minOtherTabsSelectionCount = state.data.values.map { it.otherTabsSelectionCount }.minOrNull() ?: Integer.MAX_VALUE

    return ContributorsSelectionsRange(maxAllTabSelectionCount, minAllTabSelectionCount,
                                       maxOtherTabsSelectionCount, minOtherTabsSelectionCount)
  }

  fun getContributorsSelectionsRange(): ContributorsSelectionsRange = selectionsRange

  fun getContributorStatsById(contributorId: String): ContributorSummary? = state.data[contributorId]

  @Synchronized
  fun updateContributorsLocalSummary(contributorId: String, isFromAllTab: Boolean) {
    val summary = state.data.computeIfAbsent(contributorId) { ContributorSummary() }

    summary.updateSummary(isFromAllTab)
    selectionsRange.updateRange(summary, isFromAllTab)
    incModificationCount()
  }
}

@Tag("i")
class ContributorSummary {
  @Attribute("a")
  @JvmField
  var allTabSelectionCount = 0

  @Attribute("b")
  @JvmField
  var otherTabsSelectionCount = 0

  @Attribute("c")
  @JvmField
  var allTabLastSelectedTimestamp = 0L

  @Attribute("d")
  @JvmField
  var otherTabsLastSelectedTimestamp = 0L

  fun updateSummary(isFromAllTab: Boolean) {
    val timestamp = System.currentTimeMillis()

    if (isFromAllTab) {
      allTabLastSelectedTimestamp = timestamp
      allTabSelectionCount++
    }
    else {
      otherTabsLastSelectedTimestamp = timestamp
      otherTabsSelectionCount++
    }
  }
}


data class ContributorsLocalSummaryState(
  @get:XMap(entryTagName = "e", keyAttributeName = "id") @get:Property(surroundWithTag = false)
  internal val data: MutableMap<String, ContributorSummary> = HashMap()
)

data class ContributorsSelectionsRange(
  var maxAllTabSelectionCount: Int, var minAllTabSelectionCount: Int,
  var maxOtherTabsSelectionCount: Int, var minOtherTabsSelectionCount: Int
) {
  fun updateRange(newContributorSummary: ContributorSummary, isFromAllTab: Boolean) {
    if (isFromAllTab) {
      maxAllTabSelectionCount = max(newContributorSummary.allTabSelectionCount, maxAllTabSelectionCount)
      minAllTabSelectionCount = min(newContributorSummary.allTabSelectionCount, minAllTabSelectionCount)
    }
    else {
      maxOtherTabsSelectionCount = max(newContributorSummary.otherTabsSelectionCount, maxOtherTabsSelectionCount)
      minOtherTabsSelectionCount = min(newContributorSummary.otherTabsSelectionCount, minOtherTabsSelectionCount)
    }
  }
}
