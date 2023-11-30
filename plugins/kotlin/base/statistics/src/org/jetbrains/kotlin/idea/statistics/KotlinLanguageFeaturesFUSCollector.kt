// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.statistics.KotlinLanguageFeaturesFUSCollector.kotlinLanguageVersionField
import org.jetbrains.kotlin.psi.KtFile

object KotlinLanguageFeaturesFUSCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    // Collector ID
    private val GROUP = EventLogGroup("kotlin.ide.inspections", 2)

    val inspectionTypeField = EventFields.Enum<KotlinLanguageFeatureInspectionType>("inspection_type") { it.name.lowercase() }
    val kotlinLanguageVersionField = EventFields.StringValidatedByRegexpReference("kotlin_language_version", "version_lang_api")

    val hasDeprecatedFeatureField = EventFields.Boolean("has_deprecated_feature")
    val hasNewFeatureField = EventFields.Boolean("has_new_feature")

    private val applyQuickFixEvent = GROUP.registerVarargEvent(
        "apply.quick_fix",
        EventFields.AnonymizedPath,
        inspectionTypeField
    )

    private val inspectionUpdatedEvent = GROUP.registerVarargEvent(
        "update.inspection",
        EventFields.AnonymizedPath,
        hasDeprecatedFeatureField,
        hasNewFeatureField,
        kotlinLanguageVersionField,
        inspectionTypeField
    )

    val rangeUntilCollector = LanguageFeatureDeprecationCollector<NewAndDeprecatedFeaturesInspectionData>(
        inspectionUpdatedEvent = inspectionUpdatedEvent,
        inspectionAppliedEvent = applyQuickFixEvent,
        inspectionType = KotlinLanguageFeatureInspectionType.RANGE_UNTIL
    )

    val dataObjectCollector = LanguageFeatureDeprecationCollector<NewAndDeprecatedFeaturesInspectionData>(
        inspectionUpdatedEvent = inspectionUpdatedEvent,
        inspectionAppliedEvent = applyQuickFixEvent,
        inspectionType = KotlinLanguageFeatureInspectionType.DATA_OBJECT
    )

    val enumEntriesCollector = LanguageFeatureDeprecationCollector<DeprecatedFeaturesInspectionData>(
        inspectionUpdatedEvent = inspectionUpdatedEvent,
        inspectionAppliedEvent = applyQuickFixEvent,
        inspectionType = KotlinLanguageFeatureInspectionType.ENUM_ENTRIES
    )
}

enum class KotlinLanguageFeatureInspectionType {
    RANGE_UNTIL, DATA_OBJECT, ENUM_ENTRIES
}

/**
 * Common interface for inspections that collect data.
 * Implementing classes are expected to be immutable and implement equals/hashCode correctly.
 */
interface InspectionData {
    /**
     * Return false if this state should not be logged at all if no previous state exists,
     * for example, the inspection did not find any matches.
     * Note: If a state changed compared to a previous one, it is always logged.
     */
    fun shouldLog(): Boolean

    fun toEventPairs(): List<EventPair<*>>
}

data class DeprecatedFeaturesInspectionData(
    private val hasDeprecatedFeature: Boolean = false
) : InspectionData {
    override fun shouldLog() = hasDeprecatedFeature

    fun withDeprecatedFeature(): DeprecatedFeaturesInspectionData {
        if (hasDeprecatedFeature) return this
        return copy(hasDeprecatedFeature = true)
    }

    override fun toEventPairs(): List<EventPair<*>> {
        return listOf(
            KotlinLanguageFeaturesFUSCollector.hasDeprecatedFeatureField.with(hasDeprecatedFeature)
        )
    }
}

data class NewAndDeprecatedFeaturesInspectionData(
    private val hasDeprecatedFeature: Boolean = false,
    private val hasNewFeature: Boolean = false
) : InspectionData {
    override fun shouldLog() = hasDeprecatedFeature

    fun withDeprecatedFeature(): NewAndDeprecatedFeaturesInspectionData {
        if (hasDeprecatedFeature) return this
        return copy(hasDeprecatedFeature = true)
    }

    fun withNewFeature(): NewAndDeprecatedFeaturesInspectionData {
        if (hasNewFeature) return this
        return copy(hasNewFeature = true)
    }

    override fun toEventPairs(): List<EventPair<*>> {
        return listOf(
            KotlinLanguageFeaturesFUSCollector.hasDeprecatedFeatureField.with(hasDeprecatedFeature),
            KotlinLanguageFeaturesFUSCollector.hasNewFeatureField.with(hasNewFeature)
        )
    }
}

@Service(Service.Level.PROJECT)
class KotlinLanguageFeaturesService {
    private val recordedPaths = mutableMapOf<KotlinLanguageFeatureInspectionType, MutableMap<String, InspectionData>>()

    /**
     * Returns true if the [state] should be logged in an event for the [file], false otherwise.
     */
    @Synchronized
    fun recordInspection(file: KtFile, inspectionType: KotlinLanguageFeatureInspectionType, state: InspectionData): Boolean {
        val recordedInspections = recordedPaths.getOrPut(inspectionType) { mutableMapOf() }

        val existingData = recordedInspections[file.virtualFilePath]
        if (existingData == state) return false // do not log the same state again

        recordedInspections[file.virtualFilePath] = state
        // Do not log it if there was no previous state, and we are not interested in this state
        return existingData != null || state.shouldLog()
    }
}

class LanguageFeatureDeprecationCollector<T : InspectionData>(
    private val inspectionUpdatedEvent: VarargEventId,
    private val inspectionAppliedEvent: VarargEventId,
    private val inspectionType: KotlinLanguageFeatureInspectionType
) {
    fun logInspectionUpdated(
        file: PsiFile,
        data: T,
        languageVersion: LanguageVersion
    ) {
        if (file !is KtFile || file.virtualFile == null) return
        val service = file.project.serviceOrNull<KotlinLanguageFeaturesService>() ?: return
        if (!service.recordInspection(file, inspectionType, data)) return

        inspectionUpdatedEvent.log(
            file.project,
            EventFields.AnonymizedPath.with(file.virtualFilePath),
            kotlinLanguageVersionField.with(languageVersion.versionString),
            KotlinLanguageFeaturesFUSCollector.inspectionTypeField.with(inspectionType),
            *data.toEventPairs().toTypedArray()
        )
    }

    fun logQuickFixApplied(file: PsiFile?) {
        if (file !is KtFile || file.virtualFile == null) return
        inspectionAppliedEvent.log(
            file.project,
            EventFields.AnonymizedPath.with(file.virtualFilePath),
            KotlinLanguageFeaturesFUSCollector.inspectionTypeField.with(inspectionType)
        )
    }
}