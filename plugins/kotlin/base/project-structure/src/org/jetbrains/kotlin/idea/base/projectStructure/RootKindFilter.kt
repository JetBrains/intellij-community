// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SourceKindFilterUtils")
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

data class RootKindFilter(
    val includeProjectSourceFiles: Boolean,
    val includeLibraryClassFiles: Boolean,
    val includeLibrarySourceFiles: Boolean,
    val includeScriptsOutsideSourceRoots: Boolean,
    val includeResources: Boolean
) {
    companion object {
        @JvmField
        val everything: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = true,
            includeLibraryClassFiles = true,
            includeLibrarySourceFiles = true,
            includeScriptsOutsideSourceRoots = false,
            includeResources = false
        )

        @JvmField
        val projectFiles: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = true,
            includeLibraryClassFiles = true,
            includeLibrarySourceFiles = false,
            includeScriptsOutsideSourceRoots = false,
            includeResources = false
        )

        @JvmField
        val projectSources: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = true,
            includeLibraryClassFiles = false,
            includeLibrarySourceFiles = false,
            includeScriptsOutsideSourceRoots = false,
            includeResources = false
        )

        @JvmField
        val projectSourcesAndResources: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = true,
            includeLibraryClassFiles = false,
            includeLibrarySourceFiles = false,
            includeScriptsOutsideSourceRoots = false,
            includeResources = true
        )

        @JvmField
        val libraryClasses: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = false,
            includeLibraryClassFiles = true,
            includeLibrarySourceFiles = false,
            includeScriptsOutsideSourceRoots = false,
            includeResources = false
        )

        @JvmField
        val librarySources: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = false,
            includeLibraryClassFiles = false,
            includeLibrarySourceFiles = true,
            includeScriptsOutsideSourceRoots = false,
            includeResources = false
        )

        @JvmField
        val libraryFiles: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = false,
            includeLibraryClassFiles = true,
            includeLibrarySourceFiles = true,
            includeScriptsOutsideSourceRoots = false,
            includeResources = false
        )

        @JvmField
        val projectAndLibrarySources: RootKindFilter = RootKindFilter(
            includeProjectSourceFiles = true,
            includeLibraryClassFiles = false,
            includeLibrarySourceFiles = true,
            includeScriptsOutsideSourceRoots = false,
            includeResources = false
        )
    }
}

interface RootKindMatcher {
    companion object {
        @JvmStatic
        fun matches(project: Project, virtualFile: VirtualFile, filter: RootKindFilter): Boolean {
            val matcherService = project.service<RootKindMatcher>()
            return matcherService.matches(filter, virtualFile)
        }

        @JvmStatic
        fun matches(element: PsiElement, filter: RootKindFilter): Boolean {
            val virtualFile = when (element) {
                is PsiDirectory -> element.virtualFile
                is PsiFile -> element.virtualFile
                else -> element.containingFile?.virtualFile
            }

            return virtualFile != null && matches(element.project, virtualFile, filter)
        }
    }

    fun matches(filter: RootKindFilter, virtualFile: VirtualFile): Boolean
}

fun RootKindFilter.matches(project: Project, virtualFile: VirtualFile): Boolean {
    return RootKindMatcher.matches(project, virtualFile, this)
}

fun RootKindFilter.matches(element: PsiElement): Boolean {
    return RootKindMatcher.matches(element, this)
}