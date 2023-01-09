// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class AllowedListOfLanguageFeatures {OLD_UNTIL, NEW_RANGE_UNTIL}

class KotlinLanguageFeaturesFUSCollector : CounterUsagesCollector() {

    override fun getGroup(): EventLogGroup = GROUP

    companion object {
        private val GROUP = EventLogGroup("kotlin.language.features", 1)

        private val feature_seen = EventFields.Enum<AllowedListOfLanguageFeatures>("feature_seen")
        private val feature_used = EventFields.Enum<AllowedListOfLanguageFeatures>("feature_used")
        private val kotlinLanguageVersionField = EventFields.StringValidatedByRegexp("kotlin_language_version", "version_lang_api")

        private val feature_seen_event = GROUP.registerVarargEvent(
            "feature_seen",
            feature_seen,
            EventFields.AnonymizedPath,
            kotlinLanguageVersionField
        )

        private val feature_used_event = GROUP.registerVarargEvent(
            "feature_used",
            feature_used,
            EventFields.AnonymizedPath,
            kotlinLanguageVersionField
        )

        fun logOldUntilOccurence(
            file: VirtualFile,
            kotlinLanguageVersion: String
        ) = feature_seen_event.log(
            feature_seen.with(AllowedListOfLanguageFeatures.OLD_UNTIL),
            EventFields.AnonymizedPath.with(file.path),
            this.kotlinLanguageVersionField.with(kotlinLanguageVersion)
        )

        fun logNewRangeUntilOccurence(
            file: VirtualFile,
            kotlinLanguageVersion: String
        ) = feature_seen_event.log(
            feature_seen.with(AllowedListOfLanguageFeatures.NEW_RANGE_UNTIL),
            EventFields.AnonymizedPath.with(file.path),
            this.kotlinLanguageVersionField.with(kotlinLanguageVersion)
        )

    }
}