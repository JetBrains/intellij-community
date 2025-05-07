// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile

interface ScriptAdditionalIdeaDependenciesProvider {
    fun getRelatedModules(file: VirtualFile, project: Project): List<Module> = emptyList()
    fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> = emptyList()

    companion object {
        private val EP_NAME: ExtensionPointName<ScriptAdditionalIdeaDependenciesProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.scriptAdditionalIdeaDependenciesProvider")

        fun getRelatedModules(file: VirtualFile, project: Project): List<Module> = EP_NAME.getExtensionList(project)
            .flatMap { it.getRelatedModules(file, project) }

        fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> = EP_NAME.getExtensionList(project)
            .flatMap { it.getRelatedLibraries(file, project) }
    }
}