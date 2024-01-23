// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher
import org.jetbrains.kotlin.idea.base.projectStructure.isKotlinBinary
import org.jetbrains.kotlin.idea.base.util.KOTLIN_AWARE_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol
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

        if (virtualFile !is VirtualFileWindow && fileIndex.isUnderSourceRootOfType(virtualFile, KOTLIN_AWARE_SOURCE_ROOT_TYPES)) {
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

        val canContainClassFiles: Boolean
        val isBinary: Boolean

        if (virtualFile.isDirectory) {
            canContainClassFiles = true
            isBinary = false
        } else {
            val nameSequence = virtualFile.nameSequence
            if (nameSequence.endsWith(JavaFileType.DOT_DEFAULT_EXTENSION) ||
                nameSequence.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION)
            ) {
                canContainClassFiles = false
                isBinary = false
            } else if (
                nameSequence.endsWith(JavaClassFileType.DOT_DEFAULT_EXTENSION) ||
                nameSequence.endsWith(BuiltInSerializerProtocol.DOT_DEFAULT_EXTENSION) ||
                nameSequence.endsWith(MetadataPackageFragment.Companion.DOT_METADATA_FILE_EXTENSION)
            ) {
                canContainClassFiles = false
                isBinary = true
            } else {
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(virtualFile.nameSequence)
                // NOTE: the following is a workaround for cases when class files are under library source roots and source files are under class roots
                canContainClassFiles = fileType == ArchiveFileType.INSTANCE || virtualFile.isDirectory
                isBinary = fileType.isKotlinBinary
            }
        }

        if (correctedFilter.includeLibraryClassFiles && (isBinary || canContainClassFiles)) {
            if (fileIndex.isInLibraryClasses(virtualFile)) {
                return true
            }

          val classFileScope = when {
            correctedFilter.includeScriptDependencies -> ScriptConfigurationManager.getInstance(
              project).getAllScriptsDependenciesClassFilesScope()
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
                correctedFilter.includeScriptDependencies -> ScriptConfigurationManager.getInstance(project)
                    .getAllScriptDependenciesSourcesScope()

                else -> null
            }

            if (sourceFileScope != null &&
                sourceFileScope.contains(virtualFile) &&
                !(virtualFile !is VirtualFileWindow && fileIndex.isUnderSourceRootOfType(virtualFile, KOTLIN_AWARE_SOURCE_ROOT_TYPES))
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