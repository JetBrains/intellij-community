// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleProjectData
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * This collector collects usages of Compose libraries being used in projects.
 * We only collect target-specific libraries, so we can deduce the targets Composed is being used with.
 *
 * The current implementation relies on the names Gradle/Kotlin Multiplatform libraries are being imported
 * into IDEA. If these names ever change, this collector will have to be adapted.
 */
class ComposeLibraryUsagesCollector : LibraryUsagesCollector(
    version = 1,
    eventGroupId = "kotlin.compose.libraries",
    eventId = "used.compose",
    librariesToScanFor = setOf(
        "androidx.compose.ui:ui-android",
        "org.jetbrains.compose.ui:ui-desktop",
        "org.jetbrains.compose.ui:ui-android",
        "org.jetbrains.compose.ui:ui-wasm-js",
        "org.jetbrains.compose.ui:ui-uikitx64",
        "org.jetbrains.compose.ui:ui-uikitarm64",
        "org.jetbrains.compose.ui:ui-uikitsimarm64",
        "org.jetbrains.compose.ui:ui-js",
        "org.jetbrains.compose.ui:ui-macosx64",
        "org.jetbrains.compose.ui:ui-macosarm64",
        "org.jetbrains.compose.runtime:runtime-js"
    )
)