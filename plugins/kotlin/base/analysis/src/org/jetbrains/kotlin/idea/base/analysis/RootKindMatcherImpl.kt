// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher
import org.jetbrains.kotlin.idea.base.projectStructure.isKotlinBinary
import org.jetbrains.kotlin.idea.core.script.ucache.getAllScriptDependenciesSourcesScope
import org.jetbrains.kotlin.idea.core.script.ucache.getAllScriptsDependenciesClassFilesScope
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.ide

internal class RootKindMatcherImpl(private val project: Project) : RootKindMatcher {
    private val fileIndex by lazy { ProjectRootManager.getInstance(project).fileIndex }

    override fun matches(filter: RootKindFilter, virtualFile: VirtualFile): Boolean {
        ProgressManager.checkCanceled()

        val kotlinExcludeLibrarySources = !filter.includeLibrarySourceFiles &&
                !filter.includeScriptsOutsideSourceRoots &&
                virtualFile.isKotlinFileType()

        if (kotlinExcludeLibrarySources && !filter.includeProjectSourceFiles) {
            return false
        }

        if (virtualFile !is VirtualFileWindow && fileIndex.isInSourceContent(virtualFile)) {
            return filter.includeProjectSourceFiles
        }

        if (kotlinExcludeLibrarySources) {
            return false
        }

        val scriptConfiguration = (@Suppress("DEPRECATION") virtualFile.findScriptDefinition(project))?.compilationConfiguration
        val scriptScope = scriptConfiguration?.get(ScriptCompilationConfiguration.ide.acceptedLocations)

        val correctedFilter = if (scriptScope != null) {
            val includeEverything = scriptScope.containsAllowedLocations() || ScratchUtil.isScratch(virtualFile)

            val includeLibrariesForScripts = includeEverything || scriptScope.contains(ScriptAcceptedLocation.Libraries)
            val includeProjectSourceFilesForScripts = includeEverything
                    || scriptScope.contains(ScriptAcceptedLocation.Sources)
                    || scriptScope.contains(ScriptAcceptedLocation.Tests)

            filter.copy(
                includeProjectSourceFiles = filter.includeProjectSourceFiles && includeProjectSourceFilesForScripts,
                includeLibrarySourceFiles = filter.includeLibrarySourceFiles && includeLibrariesForScripts,
                includeLibraryClassFiles = filter.includeLibraryClassFiles && includeLibrariesForScripts,
                includeScriptDependencies = filter.includeScriptDependencies && includeLibrariesForScripts,
                includeScriptsOutsideSourceRoots = filter.includeScriptsOutsideSourceRoots && includeEverything
            )
        } else {
            filter.copy(includeScriptsOutsideSourceRoots = false)
        }

        if (correctedFilter.includeScriptsOutsideSourceRoots) {
            if (fileIndex.isInContent(virtualFile) || ScratchUtil.isScratch(virtualFile)) {
                return true
            }

            return scriptConfiguration?.get(ScriptCompilationConfiguration.ide.acceptedLocations)?.containsAllowedLocations() == true
        }

        if (!correctedFilter.includeLibraryClassFiles && !correctedFilter.includeLibrarySourceFiles) {
            return false
        }

        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(virtualFile.nameSequence)
        // NOTE: the following is a workaround for cases when class files are under library source roots and source files are under class roots
        val canContainClassFiles = fileType == ArchiveFileType.INSTANCE || virtualFile.isDirectory
        val isBinary = fileType.isKotlinBinary

        if (correctedFilter.includeLibraryClassFiles && (isBinary || canContainClassFiles)) {
            if (fileIndex.isInLibraryClasses(virtualFile)) {
                return true
            }

            val classFileScope = when {
                correctedFilter.includeScriptDependencies -> getAllScriptsDependenciesClassFilesScope(project)
                else -> null
            }

            if (classFileScope != null && classFileScope.contains(virtualFile)) {
                return true
            }
        }

        if (correctedFilter.includeLibrarySourceFiles && !isBinary) {
            if (fileIndex.isInLibrarySource(virtualFile)) {
                return true
            }

            val sourceFileScope = when {
                correctedFilter.includeScriptDependencies -> getAllScriptDependenciesSourcesScope(project)
                else -> null
            }

            if (sourceFileScope != null &&
                sourceFileScope.contains(virtualFile) &&
                !(virtualFile !is VirtualFileWindow && fileIndex.isInSourceContent(virtualFile))
            ) {
                return true
            }
        }

        return false
    }

    private fun List<ScriptAcceptedLocation>.containsAllowedLocations(): Boolean {
        return any { it == ScriptAcceptedLocation.Everywhere || it == ScriptAcceptedLocation.Project }
    }
}