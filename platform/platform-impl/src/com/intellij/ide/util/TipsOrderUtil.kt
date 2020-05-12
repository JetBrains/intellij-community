// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.Alarm
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.HttpRequests
import java.util.concurrent.TimeUnit

object TipsOrderUtil {
  private val LOG = logger<TipsOrderUtil>()
  private const val RANDOM_SHUFFLE_ALGORITHM = "default_shuffle"
  private const val TIPS_SERVER_URL = "https://feature-recommendation.analytics.aws.intellij.net/tips/v1"
  private val MAPPER = ObjectMapper()

  @Volatile
  private var serverRecommendation: ServerRecommendation? = null

  /**
   * Reorders tips to show the most useful ones in the beginning
   *
   * @return object that contains sorted tips and describes approach of how the tips are sorted
   */
  @JvmStatic
  fun sort(tips: List<TipAndTrickBean>): RecommendationDescription {
    // temporarily suggest random order if we cannot estimate quality
    return serverRecommendation?.reorder(tips) ?: shuffle(tips)
  }

  data class RecommendationDescription(val algorithm: String, val tips: List<TipAndTrickBean>, val version: String?)

  private fun shuffle(tips: List<TipAndTrickBean>): RecommendationDescription {
    return RecommendationDescription(RANDOM_SHUFFLE_ALGORITHM, tips.shuffled(), null)
  }

  class RecommendationsStartupActivity : StartupActivity.DumbAware {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication())

    override fun runActivity(project: Project) {
      if (!ApplicationManager.getApplication().isEAP || !StatisticsUploadAssistant.isSendAllowed()
          || ApplicationManager.getApplication().isHeadlessEnvironment) return
      scheduleSyncRequest()
    }

    private fun scheduleSyncRequest() {
      alarm.addRequest(Runnable {
        try {
          sync()
        }
        finally {
          alarm.addRequest({ sync() }, TimeUnit.HOURS.toMillis(3))
        }
      }, 0)
    }

    private fun sync() {
      LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)
      LOG.debug("Fetching tips order from the server: $TIPS_SERVER_URL")
      val allTips = ContainerUtil.map(TipAndTrickBean.EP_NAME.extensionList) { x: TipAndTrickBean -> x.fileName }
      val actionsSummary: Map<String, UsageInfo> = service<ActionsLocalSummary>().getActionsStats().mapValues {
        UsageInfo(it.value.times, it.value.last)
      }

      val startTimestamp = System.currentTimeMillis()
      HttpRequests.post(TIPS_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
        .connect(HttpRequests.RequestProcessor { request ->
          val bucket = EventLogConfiguration.bucket
          val tipsRequest = TipsRequest(allTips, actionsSummary, PlatformUtils.getPlatformPrefix(), bucket)
          request.write(MAPPER.writeValueAsBytes(tipsRequest))
          val recommendation = MAPPER.readValue(request.readString(), ServerRecommendation::class.java)

          if (LOG.isDebugEnabled) {
            val duration = System.currentTimeMillis() - startTimestamp
            val algorithmInfo = "${recommendation.usedAlgorithm}:${recommendation.version}"
            LOG.debug("Server recommendation made. Algorithm: $algorithmInfo. Duration: ${duration}")
          }

          serverRecommendation = recommendation
        }, null, LOG)
    }
  }

  private data class UsageInfo(val usageCount: Int, val lastUsedTimestamp: Long)

  private data class TipsRequest(
    val tips: List<String>,
    val usageInfo: Map<String, UsageInfo>,
    val ideName: String, // product code
    val bucket: Int
  )

  internal class ServerRecommendation {
    @JvmField
    var showingOrder = emptyList<String>()

    @JvmField
    var usedAlgorithm = "unknown"

    @JvmField
    var version: String? = null
  }


  private fun ServerRecommendation.reorder(tips: List<TipAndTrickBean>): RecommendationDescription? {
    val tip2index: Map<String, Int> = mutableMapOf<String, Int>().apply {
      showingOrder.forEachIndexed { index, tipFile -> put(tipFile, index) }
    }

    for (tip in tips) {
      if (tip.fileName !in tip2index) {
        LOG.error("Unknown tips file: ${tip.fileName}")
        return null
      }
    }

    return RecommendationDescription(usedAlgorithm, tips.sortedBy { tip2index[it.fileName] }, version)
  }
}