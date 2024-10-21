// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtilRt
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoListener
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.KlibCompatibilityInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NativeKlibLibraryInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.isGradleLibraryName
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.parseIDELibraryName
import org.jetbrains.kotlin.idea.versions.UnsupportedAbiVersionNotificationPanelProvider
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME

/** TODO: merge [KotlinNativeABICompatibilityChecker] in the future with [UnsupportedAbiVersionNotificationPanelProvider], KT-34525 */
@K1ModeProjectStructureApi
internal class KotlinNativeABICompatibilityChecker : ProjectActivity {
    override suspend fun execute(project: Project) : Unit = blockingContext {
        KotlinNativeABICompatibilityCheckerService.getInstance(project).runActivity()
    }
}

@Service(Service.Level.PROJECT)
@K1ModeProjectStructureApi
internal class KotlinNativeABICompatibilityCheckerService(private val project: Project): Disposable {
    fun runActivity() {
        val connection = project.messageBus.connect(this)

        connection.subscribe(LibraryInfoListener.TOPIC, object : LibraryInfoListener {
            override fun libraryInfosAdded(libraryInfos: Collection<LibraryInfo>) {
                val incompatibleLibraries =
                    synchronized(cachedIncompatibleLibraries) {
                        val libraryInfoMap = libraryInfos
                            .toIncompatibleLibraries()
                            .filterKeys { it !in cachedIncompatibleLibraries }
                            .ifEmpty { return }

                        cachedIncompatibleLibraries.addAll(libraryInfoMap.keys)
                        libraryInfoMap
                    }

                prepareNotifications(incompatibleLibraries).forEach {
                    it.notify(project)
                }
            }

            override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
                val incompatibleLibraries = libraryInfos.toIncompatibleLibraries().ifEmpty { return }

                synchronized(cachedIncompatibleLibraries) {
                    cachedIncompatibleLibraries.removeAll(incompatibleLibraries.keys)
                }
            }

        })
    }

    private fun Collection<LibraryInfo>.toIncompatibleLibraries() =
        this.filterIsInstance<NativeKlibLibraryInfo>()
            .filter { !it.compatibilityInfo.isCompatible }
            .associateBy { it.libraryRoot }

    private sealed class LibraryGroup(private val ordinal: Int) : Comparable<LibraryGroup> {
        override fun compareTo(other: LibraryGroup) = when {
            this == other -> 0
            this is FromDistribution && other is FromDistribution -> kotlinVersion.compareTo(other.kotlinVersion)
            else -> ordinal.compareTo(other.ordinal)
        }

        data class FromDistribution(val kotlinVersion: String) : LibraryGroup(0)

        object ThirdParty : LibraryGroup(1)
        object User : LibraryGroup(2)
    }

    private val cachedIncompatibleLibraries = mutableSetOf<String>()

    private fun prepareNotifications(librariesToNotify: Map<String, NativeKlibLibraryInfo>): List<Notification> {
        if (librariesToNotify.isEmpty())
            return emptyList()

        val librariesByGroups = HashMap<Pair<LibraryGroup, Boolean>, MutableList<Pair<String, String>>>()
        librariesToNotify.forEach { (libraryRoot, libraryInfo) ->
            val isOldMetadata = (libraryInfo.compatibilityInfo as? KlibCompatibilityInfo.IncompatibleMetadata)?.isOlder ?: true
            val (libraryName, libraryGroup) = parseIDELibraryName(libraryInfo)
            librariesByGroups.computeIfAbsent(libraryGroup to isOldMetadata) { mutableListOf() } += libraryName to libraryRoot
        }

        return librariesByGroups.keys.sortedWith(
            compareBy(
                { (libraryGroup, _) -> libraryGroup },
                { (_, isOldMetadata) -> isOldMetadata }
            )
        ).map { key ->

            val (libraryGroup, isOldMetadata) = key
            val libraries =
                librariesByGroups.getValue(key).sortedWith(compareBy(LIBRARY_NAME_COMPARATOR) { (libraryName, _) -> libraryName })


            val message = when (libraryGroup) {
                is LibraryGroup.FromDistribution -> {
                    val libraryNamesInOneLine = libraries
                        .joinToString(limit = MAX_LIBRARY_NAMES_IN_ONE_LINE) { (libraryName, _) -> libraryName }
                    val text = KotlinGradleCodeInsightCommonBundle.message(
                        "error.incompatible.libraries",
                        libraries.size, libraryGroup.kotlinVersion, libraryNamesInOneLine
                    )
                    val explanation = when (isOldMetadata) {
                        true -> KotlinGradleCodeInsightCommonBundle.message("error.incompatible.libraries.older")
                        false -> KotlinGradleCodeInsightCommonBundle.message("error.incompatible.libraries.newer")
                    }
                    val recipe = KotlinGradleCodeInsightCommonBundle.message(
                        "error.incompatible.libraries.recipe",
                        KotlinPluginLayout.standaloneCompilerVersion.rawVersion
                    )
                    "$text\n\n$explanation\n$recipe"
                }
                is LibraryGroup.ThirdParty -> {
                    val text = when (isOldMetadata) {
                        true -> KotlinGradleCodeInsightCommonBundle.message("error.incompatible.3p.libraries.older", libraries.size)
                        false -> KotlinGradleCodeInsightCommonBundle.message("error.incompatible.3p.libraries.newer", libraries.size)
                    }
                    val librariesLineByLine = libraries.joinToString(separator = "\n") { (libraryName, _) -> libraryName }
                    val recipe = KotlinGradleCodeInsightCommonBundle.message(
                        "error.incompatible.3p.libraries.recipe",
                        KotlinPluginLayout.standaloneCompilerVersion.rawVersion
                    )
                    "$text\n$librariesLineByLine\n\n$recipe"
                }
                is LibraryGroup.User -> {
                    val projectRoot = project.guessProjectDir()?.canonicalPath

                    fun getLibraryTextToPrint(libraryNameAndRoot: Pair<String, String>): String {
                        val (libraryName, libraryRoot) = libraryNameAndRoot

                        val relativeRoot = projectRoot?.let {
                            libraryRoot.substringAfter(projectRoot)
                                .takeIf { it != libraryRoot }
                                ?.trimStart('/', '\\')
                                ?.let { "${'$'}project/$it" }
                        } ?: libraryRoot

                        return KotlinGradleCodeInsightCommonBundle.message("library.name.0.at.1.relative.root", libraryName, relativeRoot)
                    }

                    val text = when (isOldMetadata) {
                        true -> KotlinGradleCodeInsightCommonBundle.message("error.incompatible.user.libraries.older", libraries.size)
                        false -> KotlinGradleCodeInsightCommonBundle.message("error.incompatible.user.libraries.newer", libraries.size)
                    }
                    val librariesLineByLine = libraries.joinToString(separator = "\n", transform = ::getLibraryTextToPrint)
                    val recipe = KotlinGradleCodeInsightCommonBundle.message(
                        "error.incompatible.user.libraries.recipe",
                        KotlinPluginLayout.standaloneCompilerVersion.rawVersion
                    )
                    "$text\n$librariesLineByLine\n\n$recipe"
                }
            }

            Notification(
                NOTIFICATION_GROUP_ID,
                NOTIFICATION_TITLE,
                StringUtilRt.convertLineSeparators(message, "<br/>"),
                NotificationType.ERROR
            )
        }
    }

    // returns a pair of library name and library group
    private fun parseIDELibraryName(libraryInfo: NativeKlibLibraryInfo): Pair<String, LibraryGroup> {
        val ideLibraryName = libraryInfo.library.name?.takeIf(String::isNotEmpty)
        if (ideLibraryName != null) {
            parseIDELibraryName(ideLibraryName)?.let { (kotlinVersion, libraryName) ->
                return libraryName to LibraryGroup.FromDistribution(kotlinVersion)
            }

            if (isGradleLibraryName(ideLibraryName))
                return ideLibraryName to LibraryGroup.ThirdParty
        }

        return (ideLibraryName ?: PathUtilRt.getFileName(libraryInfo.libraryRoot)) to LibraryGroup.User
    }

    override fun dispose() {
    }

    companion object {
        private val LIBRARY_NAME_COMPARATOR = Comparator<String> { libraryName1, libraryName2 ->
            when {
                libraryName1 == libraryName2 -> 0
                libraryName1 == KONAN_STDLIB_NAME -> -1 // stdlib must go the first
                libraryName2 == KONAN_STDLIB_NAME -> 1
                else -> libraryName1.compareTo(libraryName2)
            }
        }

        private const val MAX_LIBRARY_NAMES_IN_ONE_LINE = 5

        private val NOTIFICATION_TITLE get() = KotlinGradleCodeInsightCommonBundle.message("error.incompatible.libraries.title")
        private const val NOTIFICATION_GROUP_ID = "Incompatible Kotlin/Native libraries"

        fun getInstance(project: Project): KotlinNativeABICompatibilityCheckerService = project.service()
    }
}
