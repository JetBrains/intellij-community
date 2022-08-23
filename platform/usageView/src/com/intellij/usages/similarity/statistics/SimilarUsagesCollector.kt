// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class SimilarUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("similar.usages", 1)
    private val SESSION_ID = EventFields.Int("id")
    private val MOST_COMMON_USAGE_PATTERNS_SHOWN = GROUP.registerEvent("most.common.usages.shown", SESSION_ID)
    private val MOST_COMMON_USAGE_PATTERNS_REFRESH_CLICKED = GROUP.registerEvent("most.common.usage.patterns.refresh.clicked", SESSION_ID)
    private val LINK_TO_SIMILAR_USAGES_FROM_USAGE_PREVIEW_CLICKED = GROUP.registerEvent("link.to.similar.usage.clicked", SESSION_ID)
    private val SHOW_SIMILAR_USAGES_LINK_CLICKED = GROUP.registerEvent("show.similar.usages.link.clicked", SESSION_ID)
    private val MORE_CLUSTERS_LOADED = GROUP.registerEvent("more.clusters.loaded", SESSION_ID)
    private val MORE_USAGES_LOADED = GROUP.registerEvent("more.usages.loaded", SESSION_ID)

    @JvmStatic
    fun logMoreSimilarUsagePatternsShow(sessionId: Int) {
      MOST_COMMON_USAGE_PATTERNS_SHOWN.log(sessionId)
    }

    @JvmStatic
    fun logMostCommonUsagePatternsRefreshClicked(sessionId: Int) {
      MOST_COMMON_USAGE_PATTERNS_REFRESH_CLICKED.log(sessionId)
    }
    @JvmStatic
    fun logLinkToSimilarUsagesLinkFromUsagePreviewClicked(sessionId: Int) {
      LINK_TO_SIMILAR_USAGES_FROM_USAGE_PREVIEW_CLICKED.log(sessionId)
    }

    @JvmStatic
    fun logShowSimilarUsagesLinkClicked(sessionId: Int) {
      SHOW_SIMILAR_USAGES_LINK_CLICKED.log(sessionId)
    }

    @JvmStatic
    fun logMoreClustersLoaded(sessionId: Int) {
      MORE_CLUSTERS_LOADED.log(sessionId)
    }

    @JvmStatic
    fun logMoreUsagesLoaded(sessionId: Int) {
      MORE_USAGES_LOADED.log(sessionId)
    }
  }
}