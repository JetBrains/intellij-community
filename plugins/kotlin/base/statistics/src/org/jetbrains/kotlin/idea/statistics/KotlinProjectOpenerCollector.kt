// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import java.nio.file.Path
import kotlin.io.path.readText

private const val RECEIPT_FILE_NAME: String = "receipt.json"

/**
 * This collector sends information from the receipt.json file about the source from which the user arrived (for example, from start.ktor.io,
 * or from any other sites that create projects with a necessary receipt.json format).
 * Fields are validated as strings to allow adding values from the FUS server.
 *
 * When creating new campaigns, a single framework name should be associated with the campaign name. This correspondence is kept
 * in some excel file, knowledge keeper Leonid Stashevsky.
 *
 * The collector reads the receipt.json file only once per project. It does not delete the file.
 * [KotlinProjectOpenerState] records that the file was read and transformed to an event.
 */
internal class KotlinProjectOpenerCollector : ProjectUsagesCollector() {
    private val GROUP = EventLogGroup("kotlin.project.opener", 1)

    private val utmSourceList = listOf(
        "google",
        "twitter",
        "facebook",
        "linkedin",
        "instagram",
        "youtube.com",
        "newsletter",
        "reddit",
        "kotlinlang.org",
        "jetbrains.com",
        "other"
    )

    private val utmMediumList =
        listOf("social", "referral", "cpc", "email", "banner", "conference", "organic", "integration", "sticky_banner", "other")

    private val UTM_SOURCE = EventFields.String("utm_source", utmSourceList)
    private val UTM_MEDIUM = EventFields.String("utm_medium", utmMediumList)
    private val UTM_CAMPAIGN = EventFields.StringValidatedByInlineRegexp("utm_campaign", "ktor3-wave2|organic|other|c-(\\d)+")

    private val PROJECT_OPENED_FROM_WEBSITE = GROUP.registerEvent(
        "project.opened.from.website",
        UTM_SOURCE, UTM_MEDIUM, UTM_CAMPAIGN
    )

    override fun getGroup(): EventLogGroup = GROUP

    override suspend fun collect(project: Project): Set<MetricEvent> {
        val openerState = project.service<KotlinProjectOpenerState>()
        if (openerState.isReceiptProcessed) return emptySet()

        val receiptFile = getReceiptFile(project) ?: return emptySet()
        val wizardReceipt = readReceiptFile(openerState, receiptFile) ?: return emptySet()

        return setOf(wizardReceipt.toMetricEvent())
    }

    private fun getReceiptFile(project: Project): Path? {
        val projectStore = project.stateStore as? IProjectStore
        val projectFileDir = projectStore?.directoryStorePath ?: return null

        return projectFileDir.resolve(RECEIPT_FILE_NAME)
    }

    private fun readReceiptFile(openerState: KotlinProjectOpenerState, receiptPath: Path): KotlinProjectOpenEvent? {
        return runCatching {
            readAndDeserializeJson(openerState, receiptPath)
        }.getOrNull()
    }

    private fun readAndDeserializeJson(openerState: KotlinProjectOpenerState, receiptPath: Path): KotlinProjectOpenEvent {
        val text = receiptPath.readText()

        val event = try {
            KotlinProjectOpenerJson.decodeFromString<KotlinProjectOpenEvent>(text)
        } catch (e: Exception) {
            Logger.getInstance(this::class.java).error(
                KotlinExceptionWithAttachments(e.message, e)
                    .withAttachment("text.json", text)
            )
            throw e
        } finally {
            openerState.markReceiptProcessed()
        }

        return event
    }

    private fun KotlinProjectOpenEvent.toMetricEvent(): MetricEvent {
        val (utmSource, utmMedium, utmCampaign) = spec.parameters

        return PROJECT_OPENED_FROM_WEBSITE.metric(utmSource, utmMedium, utmCampaign)
    }
}

private val KotlinProjectOpenerJson = Json { ignoreUnknownKeys = true }

@Service(Level.PROJECT)
@State(
    name = "KotlinProjectOpener",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
    reportStatistic = false,
)
internal class KotlinProjectOpenerState :
    SerializablePersistentStateComponent<KotlinProjectOpenerState.State>(State()) {

    @Serializable
    data class State(
        @JvmField val receiptProcessed: Boolean = false
    )

    val isReceiptProcessed: Boolean
        get() = state.receiptProcessed

    fun markReceiptProcessed() {
        updateState { it.copy(receiptProcessed = true) }
    }
}

@Serializable
internal data class KotlinProjectOpenEvent(
    @SerialName("spec") val spec: Spec,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("uid") val uid: String? = null
) {
    @Serializable
    data class Spec(
        @SerialName("template_id") val template: String,
        @SerialName("parameters") val parameters: Parameters
    )

    @Serializable
    data class Parameters(
        @SerialName("utm_source") val utmSource: String,
        @SerialName("utm_medium") val utmMedium: String,
        @SerialName("utm_campaign") val utmCampaign: String
    )
}
