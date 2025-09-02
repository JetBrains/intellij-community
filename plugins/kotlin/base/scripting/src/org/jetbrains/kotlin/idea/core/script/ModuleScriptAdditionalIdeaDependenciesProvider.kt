// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.compilerAllowsAnyScriptsInSourceRoots
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.hasNoExceptionsToBeUnderSourceRoot
import org.jetbrains.kotlin.idea.isStandaloneKotlinScript
import org.jetbrains.kotlin.utils.addIfNotNull

class ModuleScriptAdditionalIdeaDependenciesProvider : ScriptAdditionalIdeaDependenciesProvider {
    override fun getRelatedModules(file: VirtualFile, project: Project): List<Module> =
        if (!compilerAllowsAnyScriptsInSourceRoots(project) &&
            RootKindFilter.projectSources.matches(project, file) &&
            file.isStandaloneKotlinScript(project) &&
            file.hasNoExceptionsToBeUnderSourceRoot()
        ) {
            emptyList()
        } else {
            listOfNotNull(ProjectFileIndex.getInstance(project).getModuleForFile(file))
        }

    override fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> {
        val result = linkedSetOf<Library>()
        getRelatedModules(file, project).forEach {
            moduleDependencyEnumerator(it).withoutDepModules().forEach { orderEntry ->
                if (orderEntry is LibraryOrderEntry) {
                    result.addIfNotNull(orderEntry.library)
                }
                true
            }
        }
        return result.toList()
    }

    private fun moduleDependencyEnumerator(it: Module): OrderEnumerator {
        return ModuleRootManager.getInstance(it).orderEntries()
            .compileOnly().withoutSdk().recursively().exportedOnly()
    }
}