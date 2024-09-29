// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider

interface ScriptDependencyAware {
    fun getAllScriptDependenciesSources(): Collection<VirtualFile>
    fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile>

    fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope
    fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope

    fun getScriptDependenciesClassFilesScope(virtualFile: VirtualFile): GlobalSearchScope
    fun getScriptDependenciesClassFiles(virtualFile: VirtualFile): Collection<VirtualFile>

    fun getFirstScriptsSdk(): Sdk?
    fun getScriptSdk(virtualFile: VirtualFile): Sdk?

    companion object {
        fun getInstance(project: Project): ScriptDependencyAware {
            return if (KotlinPluginModeProvider.isK2Mode()) {
                project.serviceIfCreated<ScriptConfigurationsProvider>() as? ScriptDependencyAware ?: EMPTY
            } else {
                ScriptConfigurationManager.getInstance(project)
            }
        }

        val EMPTY = object : ScriptDependencyAware {
            override fun getAllScriptDependenciesSources(): Collection<VirtualFile> = listOf()
            override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> = listOf()
            override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope = GlobalSearchScope.EMPTY_SCOPE
            override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope = GlobalSearchScope.EMPTY_SCOPE
            override fun getScriptDependenciesClassFilesScope(virtualFile: VirtualFile): GlobalSearchScope = GlobalSearchScope.EMPTY_SCOPE
            override fun getScriptDependenciesClassFiles(virtualFile: VirtualFile): Collection<VirtualFile> = listOf()
            override fun getFirstScriptsSdk(): Sdk? = null
            override fun getScriptSdk(virtualFile: VirtualFile): Sdk? = null
        }
    }
}