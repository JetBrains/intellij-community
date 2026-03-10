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
 * This collector collects usages of libraries being used in projects.
 *
 * The current implementation relies on the names Gradle/Kotlin Multiplatform libraries are being imported
 * into IDEA. If these names ever change, this collector will have to be adapted.
 */
abstract class LibraryUsagesCollector(
    eventGroupId: String,
    eventId: String,
    version: Int,
    private val librariesToScanFor: Set<String>
) : ProjectUsagesCollector() {
    private val GROUP = EventLogGroup(eventGroupId, version)

    override fun requiresReadAccess(): Boolean = true

    override fun getGroup(): EventLogGroup = GROUP

    private val USED_LIBRARY = GROUP.registerEvent(
        eventId,
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

    private data class UsedLibrary(val library: String, val version: String)

    private fun parseLibrary(orderEntry: LibraryOrderEntry): UsedLibrary? {
        val name = orderEntry.library?.name ?: return null
        val libraryDefinition = extractLibraryDefinition(name) ?: return null
        val artifactCoordinates = "${libraryDefinition.groupId}:${libraryDefinition.artifactId}"
        if (artifactCoordinates !in librariesToScanFor) return null
        return UsedLibrary(artifactCoordinates, libraryDefinition.version)
    }

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val allLibraries = mutableSetOf<UsedLibrary>()
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
        return allLibraries.mapTo(mutableSetOf()) { USED_LIBRARY.metric(it.library, it.version) }
    }
}