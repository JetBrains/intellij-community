// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleCodeInsightCommon.native

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.PathUtilRt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinLibraries
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.KlibCompatibilityInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.compatibilityInfo
import org.jetbrains.kotlin.idea.base.projectStructure.openapiLibrary
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.isGradleLibraryName
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.parseIDELibraryName
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinNotConfiguredSuppressedModulesState
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import java.util.function.Function
import javax.swing.JComponent

internal class KotlinNativeABICompatibilityChecker : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (!file.isKotlinFileType()) {
            return null
        }
        try {
            if (
                DumbService.isDumb(project)
                || isUnitTestMode()
                || KotlinNotConfiguredSuppressedModulesState.isSuppressed(project)
            ) {
                return null
            }

            val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null

            val incompatibleLibs = getIncompatibleNativeLibraries(module)
                .takeUnless(Collection<KaLibraryModule>::isEmpty)
                ?: return null

            return Function { doCreate(it, project, incompatibleLibs) }
        } catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).runWhenSmart { updateNotifications(project) }
        }

        return null
    }

    private fun updateNotifications(project: Project) {
        invokeLater {
            if (!project.isDisposed) {
                EditorNotifications.getInstance(project).updateAllNotifications()
            }
        }
    }

    private fun doCreate(
        fileEditor: FileEditor,
        project: Project,
        incompatibleLibs: List<KaLibraryModule>
    ): EditorNotificationPanel {
        val answer = EditorNotificationPanel(fileEditor)
        answer.text(prepareNotifications(project, incompatibleLibs).joinToString("<br/>"))
        return answer
    }

    fun getIncompatibleNativeLibraries(module: Module): List<KaLibraryModule> {
        val project = module.project
        return ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>()
            .flatMap { it.library?.toKaLibraryModules(project).orEmpty() }
            .filter { libraryModule -> libraryModule.getKotlinLibraries(project).any { !it.compatibilityInfo.isCompatible} }
    }

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

    private fun prepareNotifications(project: Project, librariesToNotify: List<KaLibraryModule>): List<String> {
        if (librariesToNotify.isEmpty())
            return emptyList()

        val librariesByGroups = HashMap<Pair<LibraryGroup, Boolean>, MutableList<Pair<String, String>>>()
        librariesToNotify.forEach { lib ->
            val isOldMetadata = lib.getKotlinLibraries(project)
                .any { (it.compatibilityInfo as? KlibCompatibilityInfo.IncompatibleMetadata)?.isOlder ?: true }

            val library = lib.openapiLibrary ?: return@forEach
            val libraryRoot = library.rootProvider.getUrls(OrderRootType.CLASSES).firstOrNull() ?: return@forEach
            val (libraryName, libraryGroup) = parseIDELibraryName(libraryRoot, library)
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


            when (libraryGroup) {
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

        }
    }

    // returns a pair of library name and library group
    private fun parseIDELibraryName(libraryRoot: String, library: Library): Pair<String, LibraryGroup> {
        val ideLibraryName = library.name?.takeIf(String::isNotEmpty)
        if (ideLibraryName != null) {
            parseIDELibraryName(ideLibraryName)?.let { (kotlinVersion, libraryName) ->
                return libraryName to LibraryGroup.FromDistribution(kotlinVersion)
            }

            if (isGradleLibraryName(ideLibraryName))
                return ideLibraryName to LibraryGroup.ThirdParty
        }

        return (ideLibraryName ?: PathUtilRt.getFileName(libraryRoot)) to LibraryGroup.User
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
    }
}
