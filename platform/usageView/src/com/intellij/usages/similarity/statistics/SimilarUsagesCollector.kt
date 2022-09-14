// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.usages.similarity.clustering.ClusteringSearchSession

class SimilarUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("similar.usages", 3)
    private val SESSION_ID = EventFields.Int("id")
    private val NUMBER_OF_LOADED = EventFields.Int("number_of_loaded")
    private val MOST_COMMON_USAGE_PATTERNS_SHOWN = GROUP.registerEvent("most.common.usages.shown", SESSION_ID)
    private val MOST_COMMON_USAGE_PATTERNS_REFRESH_CLICKED = GROUP.registerEvent("most.common.usage.patterns.refresh.clicked", SESSION_ID)
    private val LINK_TO_SIMILAR_USAGES_FROM_USAGE_PREVIEW_CLICKED = GROUP.registerEvent("link.to.similar.usage.clicked", SESSION_ID)
    private val SHOW_SIMILAR_USAGES_LINK_CLICKED = GROUP.registerEvent("show.similar.usages.link.clicked", SESSION_ID)
    private val MORE_CLUSTERS_LOADED = GROUP.registerEvent("more.clusters.loaded", SESSION_ID, NUMBER_OF_LOADED)
    private val MORE_USAGES_LOADED = GROUP.registerEvent("more.usages.loaded", SESSION_ID, NUMBER_OF_LOADED)
    private val MORE_NON_CLUSTERED_USAGES_LOADED = GROUP.registerEvent("more.non.clustered.usage.loaded", SESSION_ID, NUMBER_OF_LOADED)

    @JvmStatic
    fun logMoreSimilarUsagePatternsShow(project: Project, session: ClusteringSearchSession) {
      MOST_COMMON_USAGE_PATTERNS_SHOWN.log(project, session.uniqueId)
    }

    @JvmStatic
    fun logMostCommonUsagePatternsRefreshClicked(project: Project, session: ClusteringSearchSession) {
      MOST_COMMON_USAGE_PATTERNS_REFRESH_CLICKED.log(project, session.uniqueId)
    }

    @JvmStatic
    fun logLinkToSimilarUsagesLinkFromUsagePreviewClicked(project: Project, session: ClusteringSearchSession) {
      LINK_TO_SIMILAR_USAGES_FROM_USAGE_PREVIEW_CLICKED.log(project, session.uniqueId)
    }

    @JvmStatic
    fun logShowSimilarUsagesLinkClicked(project: Project, session: ClusteringSearchSession) {
      SHOW_SIMILAR_USAGES_LINK_CLICKED.log(project, session.uniqueId)
    }

    @JvmStatic
    fun logMoreClustersLoaded(project: Project, session: ClusteringSearchSession, numberOfAddedUsages: Int) {
      MORE_CLUSTERS_LOADED.log(project, session.uniqueId, numberOfAddedUsages)
    }

    @JvmStatic
    fun logMoreNonClusteredUsagesLoaded(project: Project, session: ClusteringSearchSession, numberOfAddedUsages: Int) {
      MORE_NON_CLUSTERED_USAGES_LOADED.log(project, session.uniqueId, numberOfAddedUsages)
    }

    @JvmStatic
    fun logMoreUsagesLoaded(project: Project, session: ClusteringSearchSession, numberOfAddedUsages: Int) {
      MORE_USAGES_LOADED.log(project, session.uniqueId, numberOfAddedUsages)
    }
  }
}