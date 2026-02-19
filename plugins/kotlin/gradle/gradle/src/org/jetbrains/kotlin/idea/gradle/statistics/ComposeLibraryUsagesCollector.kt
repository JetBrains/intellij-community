// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
class ComposeLibraryUsagesCollector : ProjectUsagesCollector() {
    private val GROUP = EventLogGroup("kotlin.compose.libraries", 1)

    override fun requiresReadAccess(): Boolean = true

    override fun getGroup(): EventLogGroup = GROUP

    private val librariesToScanFor = setOf(
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

    private val USED_COMPOSE = GROUP.registerEvent(
        "used.compose",
        EventFields.String("library", librariesToScanFor.toList()),
        EventFields.Version,
    )

    /**
     *  This regex encodes the way libraries are imported into IDEA by the Gradle importer.
     *  These names are implementation details, so we cannot rely on them staying this way forever, but it is very
     *  unlikely that they will change in the future.
     *  Depending on this logic allows us to scan for the libraries we want very cheaply.
     */
    private val libraryRegex = Regex("""(?:Gradle: )?([\w-_.+]+):([\w-_.+]+):([\w-_.+]+)(?:@.*)?""")

    @ApiStatus.Internal
    data class LibraryDefinition(val groupId: String, val artifactId: String, val version: String) {
        fun toLibraryCoordinates(): String = "$groupId:$artifactId"
    }

    @ApiStatus.Internal
    fun extractLibraryDefinition(name: String): LibraryDefinition? {
        val matchedLibrary = libraryRegex.matchEntire(name) ?: return null
        val groupId = matchedLibrary.groupValues[1]
        val artifactId = matchedLibrary.groupValues[2]
        val version = matchedLibrary.groupValues[3]
        return LibraryDefinition(groupId, artifactId, version)
    }

    private data class ComposeLibrary(val library: String, val version: String)

    private fun parseLibrary(orderEntry: LibraryOrderEntry): ComposeLibrary? {
        val name = orderEntry.library?.name ?: return null
        val libraryDefinition = extractLibraryDefinition(name) ?: return null
        val artifactCoordinates = "${libraryDefinition.groupId}:${libraryDefinition.artifactId}"
        if (artifactCoordinates !in librariesToScanFor) return null
        return ComposeLibrary(artifactCoordinates, libraryDefinition.version)
    }

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val allLibraries = mutableSetOf<ComposeLibrary>()
        for (module in project.modules) {
            // check for cancellation to avoid freezes in large projects
            ProgressManager.checkCanceled()

            val gradleModuleData = GradleUtil.findGradleModuleData(module) ?: continue
            val gradleProjectData = ExternalSystemApiUtil.find(gradleModuleData, KotlinGradleProjectData.KEY)?.data
            // If the module does not have the Kotlin plugin (either directly or through KMP), then we skip it
            if (gradleProjectData == null || (!gradleProjectData.isHmpp && !gradleProjectData.hasKotlinPlugin)) continue

            val moduleRoots = ModuleRootManager.getInstance(module)
            moduleRoots.orderEntries.filterIsInstance<LibraryOrderEntry>()
                .mapNotNullTo(allLibraries, ::parseLibrary)
        }
        return allLibraries.mapTo(mutableSetOf()) { USED_COMPOSE.metric(it.library, it.version) }
    }
}