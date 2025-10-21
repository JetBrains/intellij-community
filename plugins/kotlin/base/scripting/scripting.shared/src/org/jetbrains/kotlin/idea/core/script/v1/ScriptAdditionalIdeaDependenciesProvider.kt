// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly

interface ScriptAdditionalIdeaDependenciesProvider {
    fun getRelatedModules(file: VirtualFile, project: Project): List<Module> = emptyList()
    fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> = emptyList()

    companion object {
        @TestOnly
        val EP_NAME: ExtensionPointName<ScriptAdditionalIdeaDependenciesProvider> =
            ExtensionPointName.Companion.create("org.jetbrains.kotlin.scriptAdditionalIdeaDependenciesProvider")

        fun getRelatedModules(file: VirtualFile, project: Project): List<Module> = EP_NAME.getExtensionList(project)
            .flatMap { it.getRelatedModules(file, project) }

        fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> = EP_NAME.getExtensionList(project)
            .flatMap { it.getRelatedLibraries(file, project) }
    }
}