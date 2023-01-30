// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.vfs.VirtualFile

class KotlinLanguageFeaturesFUSCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    object RangeUntil {
        private const val RANGE_UNTIL_FEATURE = "range_until_feature"
        private val rangeUntilEventField = EventFields.Enum<RangeUntilLanguageFeature>(RANGE_UNTIL_FEATURE)
        private enum class RangeUntilLanguageFeature {
            OLD_UNTIL_SEEN,
            NEW_RANGE_UNTIL_SEEN,
            UNTIL_TO_RANGE_UNTIL_QUICK_FIX_IS_APPLIED
        }

        private val rangeUntilFeatureEvent = GROUP.registerVarargEvent(
            RANGE_UNTIL_FEATURE,
            rangeUntilEventField,
            EventFields.AnonymizedPath,
            kotlinLanguageVersionField
        )

        fun logOldUntilOccurence(file: VirtualFile, kotlinLanguageVersion: String): Unit =
            log(RangeUntilLanguageFeature.OLD_UNTIL_SEEN, file, kotlinLanguageVersion)

        fun logNewRangeUntilOccurence(file: VirtualFile, kotlinLanguageVersion: String): Unit =
            log(RangeUntilLanguageFeature.NEW_RANGE_UNTIL_SEEN, file, kotlinLanguageVersion)

        fun logUntilToRangeUntilQuickFixIsApplied(file: VirtualFile, kotlinLanguageVersion: String): Unit =
            log(RangeUntilLanguageFeature.UNTIL_TO_RANGE_UNTIL_QUICK_FIX_IS_APPLIED, file, kotlinLanguageVersion)

        private fun log(
            event: RangeUntilLanguageFeature,
            file: VirtualFile,
            kotlinLanguageVersion: String,
        ): Unit = rangeUntilFeatureEvent.log(
            rangeUntilEventField.with(event),
            EventFields.AnonymizedPath.with(file.path),
            kotlinLanguageVersionField.with(kotlinLanguageVersion)
        )
    }

    object EnumEntries {
        private const val ENUM_ENTRIES_FEATURE = "enum_entries_feature"
        private val enumEntriesEventField = EventFields.Enum<EnumEntriesLanguageFeature>(ENUM_ENTRIES_FEATURE)
        private enum class EnumEntriesLanguageFeature {
            VALUES_TO_ENTRIES_QUICK_FIX_IS_SUGGESTED,
            VALUES_TO_ENTRIES_QUICK_FIX_IS_APPLIED,
        }

        private val enumEntriesFeatureEvent = GROUP.registerVarargEvent(
            ENUM_ENTRIES_FEATURE,
            enumEntriesEventField,
            EventFields.AnonymizedPath
        )

        fun logValuesToEntriesQuickFixIsSuggested(file: VirtualFile): Unit =
            log(EnumEntriesLanguageFeature.VALUES_TO_ENTRIES_QUICK_FIX_IS_SUGGESTED, file)

        fun logValuesToEntriesQuickFixIsApplied(file: VirtualFile): Unit =
            log(EnumEntriesLanguageFeature.VALUES_TO_ENTRIES_QUICK_FIX_IS_APPLIED, file)

        private fun log(event: EnumEntriesLanguageFeature, file: VirtualFile): Unit =
            enumEntriesFeatureEvent.log(enumEntriesEventField.with(event), EventFields.AnonymizedPath.with(file.path))
    }
}

private val GROUP = EventLogGroup("kotlin.language.features", 1)
private val kotlinLanguageVersionField = EventFields.StringValidatedByRegexp("kotlin_language_version", "version_lang_api")
