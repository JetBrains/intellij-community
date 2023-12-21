// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.base.util.containsNonScriptKotlinFile
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.formatter.KotlinFormatterUsageCollector.KotlinFormatterKind.*

class KotlinFormatterUsageCollector : ProjectUsagesCollector() {
    override fun requiresReadAccess() = true

    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        if (KotlinPlatformUtils.isAndroidStudio || project.runReadActionInSmartMode { !project.containsNonScriptKotlinFile() }) {
            return emptySet()
        }

        return setOf(
            settingsEvent.metric(
                value1 = getKotlinFormatterKind(project),
                value2 = getPluginInfo(KotlinFormatterUsageCollector::class.java),
            )
        )
    }

    private val GROUP = EventLogGroup("kotlin.ide.formatter", 4)
    private val settingsEvent = GROUP.registerEvent(
        eventId = "settings",
        eventField1 = EventFields.Enum("kind", KotlinFormatterKind::class.java),
        eventField2 = EventFields.PluginInfo,
    )

    enum class KotlinFormatterKind {
        IDEA_CUSTOM, PROJECT_CUSTOM,

        IDEA_OFFICIAL_KOTLIN, PROJECT_OFFICIAL_KOTLIN,
        IDEA_OFFICIAL_KOTLIN_WITH_CUSTOM, PROJECT_OFFICIAL_KOTLIN_WITH_CUSTOM,
        IDEA_WITH_BROKEN_OFFICIAL_KOTLIN, PROJECT_WITH_BROKEN_OFFICIAL_KOTLIN,

        IDEA_OBSOLETE_KOTLIN, PROJECT_OBSOLETE_KOTLIN,
        IDEA_OBSOLETE_KOTLIN_WITH_CUSTOM, PROJECT_OBSOLETE_KOTLIN_WITH_CUSTOM,
        IDEA_WITH_BROKEN_OBSOLETE_KOTLIN, PROJECT_WITH_BROKEN_OBSOLETE_KOTLIN,
    }
}

private val KOTLIN_OFFICIAL_CODE_STYLE: CodeStyleSettings by lazy {
    CodeStyleSettingsManager.getInstance().cloneSettings(CodeStyle.getDefaultSettings()).also(KotlinOfficialStyleGuide::apply)
}

private val KOTLIN_OBSOLETE_CODE_STYLE: CodeStyleSettings by lazy {
    CodeStyleSettingsManager.getInstance().cloneSettings(CodeStyle.getDefaultSettings()).also(KotlinObsoleteStyleGuide::apply)
}

private fun codeStylesIsEquals(lhs: CodeStyleSettings, rhs: CodeStyleSettings): Boolean =
    lhs.kotlinCustomSettings == rhs.kotlinCustomSettings && lhs.kotlinCommonSettings == rhs.kotlinCommonSettings

fun getKotlinFormatterKind(project: Project): KotlinFormatterUsageCollector.KotlinFormatterKind {
    val isProject = CodeStyle.usesOwnSettings(project)
    val currentSettings = CodeStyle.getSettings(project)

    val codeStyleDefaults = currentSettings.kotlinCodeStyleDefaults()
    return when (val supposedCodeStyleDefaults = currentSettings.supposedKotlinCodeStyleDefaults()) {
        KotlinOfficialStyleGuide.CODE_STYLE_ID -> when {
            supposedCodeStyleDefaults != codeStyleDefaults -> paired(IDEA_WITH_BROKEN_OFFICIAL_KOTLIN, isProject)
            codeStylesIsEquals(currentSettings, KOTLIN_OFFICIAL_CODE_STYLE) -> paired(IDEA_OFFICIAL_KOTLIN, isProject)
            else -> paired(IDEA_OFFICIAL_KOTLIN_WITH_CUSTOM, isProject)
        }

        KotlinObsoleteStyleGuide.CODE_STYLE_ID -> when {
            supposedCodeStyleDefaults != codeStyleDefaults -> paired(IDEA_WITH_BROKEN_OBSOLETE_KOTLIN, isProject)
            codeStylesIsEquals(currentSettings, KOTLIN_OBSOLETE_CODE_STYLE) -> paired(IDEA_OBSOLETE_KOTLIN, isProject)
            else -> paired(IDEA_OBSOLETE_KOTLIN_WITH_CUSTOM, isProject)
        }

        else -> paired(IDEA_CUSTOM, isProject)
    }
}

private fun paired(
    kind: KotlinFormatterUsageCollector.KotlinFormatterKind,
    isProject: Boolean
): KotlinFormatterUsageCollector.KotlinFormatterKind {
    if (!isProject) return kind

    return when (kind) {
        IDEA_CUSTOM -> PROJECT_CUSTOM

        IDEA_OFFICIAL_KOTLIN -> PROJECT_OFFICIAL_KOTLIN
        IDEA_OFFICIAL_KOTLIN_WITH_CUSTOM -> PROJECT_OFFICIAL_KOTLIN_WITH_CUSTOM
        IDEA_WITH_BROKEN_OFFICIAL_KOTLIN -> PROJECT_WITH_BROKEN_OFFICIAL_KOTLIN

        IDEA_OBSOLETE_KOTLIN -> PROJECT_OBSOLETE_KOTLIN
        IDEA_OBSOLETE_KOTLIN_WITH_CUSTOM -> PROJECT_OBSOLETE_KOTLIN_WITH_CUSTOM
        IDEA_WITH_BROKEN_OBSOLETE_KOTLIN -> PROJECT_WITH_BROKEN_OBSOLETE_KOTLIN

        else -> kind
    }
}
