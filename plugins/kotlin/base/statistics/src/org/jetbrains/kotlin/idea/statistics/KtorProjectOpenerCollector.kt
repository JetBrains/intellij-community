// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

private const val RECEIPT_FILE_NAME: String = "receipt.json"

/**
 * This collector sends information from the receipt.json file about the source from which
 * the user arrived at the site start.ktor.io.
 * Fields are validated as strings to allow for adding values from the FUS server.
 *
 * The receipt.json file will be deleted once it has been read.
 */
internal class KtorProjectOpenerCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("ktor.project.opener", 5)

  private val utmSourceList = listOf("google",
                                     "twitter",
                                     "facebook",
                                     "linkedin",
                                     "instagram",
                                     "youtube.com",
                                     "newsletter",
                                     "reddit",
                                     "kotlinlang.org",
                                     "jetbrains.com",
                                     "other")

  private val utmMediumList = listOf("social", "referral", "cpc", "email", "banner", "conference", "organic", "integration", "sticky_banner", "other")

  private val utmCampaignList = listOf("ktor3-wave2", "organic", "other")

  private val UTM_SOURCE = EventFields.String("utm_source", utmSourceList)
  private val UTM_MEDIUM = EventFields.String("utm_medium", utmMediumList)
  private val UTM_CAMPAIGN = EventFields.String("utm_campaign", utmCampaignList)

  private val KTOR_PROJECT_OPENED_FROM_WEBSITE = GROUP.registerEvent("project.opened.from.website",
                                                                                        UTM_SOURCE, UTM_MEDIUM, UTM_CAMPAIGN)

  override fun requiresReadAccess(): Boolean = true

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val receiptFile = getReceiptFile(project) ?: return emptySet()
    val wizardReceipt = readReceiptFile(receiptFile) ?: return emptySet()

    return setOf(wizardReceipt.toMetricEvent())
  }

  private fun getReceiptFile(project: Project): Path? {
    val projectStore = project.stateStore as? IProjectStore
    val projectFileDir = projectStore?.directoryStorePath ?: return null

    return projectFileDir.resolve(RECEIPT_FILE_NAME)
  }

  private fun readReceiptFile(receiptPath: Path): KtorOpenEvent? {
    return runCatching {
      readAndDeserializeJson(receiptPath)
    }.getOrNull()
  }

  private fun readAndDeserializeJson(receiptPath: Path): KtorOpenEvent {
    val text = receiptPath.readText()

    val json = Json { ignoreUnknownKeys = true }
    val event = json.decodeFromString<KtorOpenEvent>(text)

    receiptPath.deleteIfExists()

    return event
  }

  private fun KtorOpenEvent.toMetricEvent(): MetricEvent {
    val (utmSource, utmMedium, utmCampaign) = spec.parameters

    return KTOR_PROJECT_OPENED_FROM_WEBSITE.metric(utmSource, utmMedium, utmCampaign)
  }
}
