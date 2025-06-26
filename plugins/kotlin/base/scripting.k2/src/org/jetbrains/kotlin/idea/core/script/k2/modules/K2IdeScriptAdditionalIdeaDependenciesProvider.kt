// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import org.jetbrains.annotations.TestOnly

interface K2IdeScriptAdditionalIdeaDependenciesProvider {
    fun getRelatedModules(file: VirtualFile, project: Project): List<ModuleEntity> = emptyList()
    fun getRelatedLibraries(file: VirtualFile, project: Project): List<LibraryDependency> = emptyList()

    companion object {
        @TestOnly
        val EP_NAME: ExtensionPointName<K2IdeScriptAdditionalIdeaDependenciesProvider> =
            ExtensionPointName.Companion.create("org.jetbrains.kotlin.k2IdeScriptAdditionalIdeaDependenciesProvider")

        fun getRelatedModules(file: VirtualFile, project: Project): List<ModuleEntity> = EP_NAME.extensionList
            .flatMap { it.getRelatedModules(file, project) }

        fun getRelatedLibraries(file: VirtualFile, project: Project): List<LibraryDependency> = EP_NAME.extensionsIfPointIsRegistered
            .flatMap { it.getRelatedLibraries(file, project) }
    }
}