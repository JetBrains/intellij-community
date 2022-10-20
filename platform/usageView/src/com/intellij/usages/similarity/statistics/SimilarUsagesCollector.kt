// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.usages.UsageView
import com.intellij.usages.impl.UsageViewStatisticsCollector.Companion.USAGE_VIEW
import javax.swing.JComponent

class SimilarUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("similar.usages", 6)
    private val COMPONENT_CLASS = EventFields.Class("component")
    private val NUMBER_OF_LOADED = EventFields.Int("number_of_loaded")
    private val NAVIGATE_TO_USAGE_CLICKED = GROUP.registerVarargEvent("navigate.to.usage.clicked", COMPONENT_CLASS, USAGE_VIEW)
    private val MOST_COMMON_USAGE_PATTERNS_SHOWN = GROUP.registerEvent("most.common.usages.shown", USAGE_VIEW)
    private val MOST_COMMON_USAGE_PATTERNS_REFRESH_CLICKED = GROUP.registerEvent("most.common.usage.patterns.refresh.clicked", USAGE_VIEW)
    private val LINK_TO_SIMILAR_USAGES_FROM_USAGE_PREVIEW_CLICKED = GROUP.registerEvent("link.to.similar.usage.clicked", USAGE_VIEW)
    private val SHOW_SIMILAR_USAGES_LINK_CLICKED = GROUP.registerEvent("show.similar.usages.link.clicked", USAGE_VIEW)
    private val MORE_CLUSTERS_LOADED = GROUP.registerEvent("more.clusters.loaded", USAGE_VIEW, NUMBER_OF_LOADED)
    private val MORE_USAGES_LOADED = GROUP.registerEvent("more.usages.loaded", USAGE_VIEW, NUMBER_OF_LOADED)
    private val MORE_NON_CLUSTERED_USAGES_LOADED = GROUP.registerEvent("more.non.clustered.usage.loaded", USAGE_VIEW, NUMBER_OF_LOADED)

    @JvmStatic
    fun logMostCommonUsagePatternsShown(project: Project, usageView: UsageView) {
      MOST_COMMON_USAGE_PATTERNS_SHOWN.log(project, usageView)
    }

    @JvmStatic
    fun logNavigateToUsageClicked(project: Project, component: Class<out JComponent>?, usageView: UsageView) {
      NAVIGATE_TO_USAGE_CLICKED.log(project, COMPONENT_CLASS.with(component), USAGE_VIEW.with(usageView))
    }

    @JvmStatic
    fun logMostCommonUsagePatternsRefreshClicked(project: Project, usageView: UsageView) {
      MOST_COMMON_USAGE_PATTERNS_REFRESH_CLICKED.log(project, usageView)
    }

    @JvmStatic
    fun logLinkToSimilarUsagesLinkFromUsagePreviewClicked(project: Project, usageView: UsageView) {
      LINK_TO_SIMILAR_USAGES_FROM_USAGE_PREVIEW_CLICKED.log(project, usageView)
    }

    @JvmStatic
    fun logShowSimilarUsagesLinkClicked(project: Project, usageView: UsageView) {
      SHOW_SIMILAR_USAGES_LINK_CLICKED.log(project, usageView)
    }

    @JvmStatic
    fun logMoreClustersLoaded(project: Project, usageView: UsageView, numberOfAddedUsages: Int) {
      MORE_CLUSTERS_LOADED.log(project, usageView, numberOfAddedUsages)
    }

    @JvmStatic
    fun logMoreNonClusteredUsagesLoaded(project: Project, usageView: UsageView, numberOfAddedUsages: Int) {
      MORE_NON_CLUSTERED_USAGES_LOADED.log(project, usageView, numberOfAddedUsages)
    }

    @JvmStatic
    fun logMoreUsagesLoaded(project: Project, usageView: UsageView, numberOfAddedUsages: Int) {
      MORE_USAGES_LOADED.log(project, usageView, numberOfAddedUsages)
    }
  }
}