// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.NonClasspathDirectoriesScope.compose
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEPENDENCIES_SOURCES
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.ScriptDependencyAware
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDependenciesProvider
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.valueOrNull

class ScriptDependenciesData(
    val configurations: Map<VirtualFile, ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>> = mapOf(),
    val classes: Set<VirtualFile> = mutableSetOf(),
    val sources: Set<VirtualFile> = mutableSetOf(),
    val sdks: Map<Path, Sdk> = mutableMapOf(),
) {
    fun compose(other: ScriptDependenciesData): ScriptDependenciesData = ScriptDependenciesData(
        configurations + other.configurations,
        classes + other.classes,
        sources + other.sources,
        sdks + other.sdks
    )
}

class ScriptConfigurationDataProvider(project: Project) : ScriptDependenciesProvider(project), ScriptDependencyAware {
    private val scriptDependenciesData = AtomicReference(ScriptDependenciesData())

    fun notifySourceUpdated() {
        val newData = SCRIPT_DEPENDENCIES_SOURCES.getExtensions(project)
            .map { it.currentConfigurationsData }
            .fold(ScriptDependenciesData()) { acc, reference ->
                acc.compose(reference.get())
            }

        this.scriptDependenciesData.set(newData)
    }

    override fun getAllScriptDependenciesSources(): Collection<VirtualFile> =
        scriptDependenciesData.get()?.sources ?: emptyList()

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> =
        scriptDependenciesData.get().classes

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope =
        with(scriptDependenciesData.get()) {
            compose(classes.toList() + getSdkClasses())
        }

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope =
        with(scriptDependenciesData.get()) {
            compose(sources.toList() + getSdkSources())
        }

    override fun getScriptDependenciesClassFilesScope(virtualFile: VirtualFile): GlobalSearchScope =
        with(scriptDependenciesData.get()) {
            val configurationWrapper = configurations[virtualFile]?.valueOrNull()
                ?: return@with GlobalSearchScope.EMPTY_SCOPE

            val roots = toVfsRoots(configurationWrapper.dependenciesClassPath)

            val sdk = configurationWrapper.javaHome?.let { sdks[it.toPath()] }
            val sdkClasses = sdk?.rootProvider?.getFiles(OrderRootType.CLASSES)?.toList() ?: emptyList<VirtualFile>()

            compose(roots + sdkClasses)
        }

    override fun getScriptDependenciesClassFiles(virtualFile: VirtualFile): Collection<VirtualFile> {
        val dependencies =
            scriptDependenciesData.get().configurations[virtualFile]?.valueOrNull()?.dependenciesClassPath ?: return emptyList()
        return toVfsRoots(dependencies)
    }

    override fun getFirstScriptsSdk(): Sdk? =
        getProjectSdk() ?: scriptDependenciesData.get().sdks.values.firstOrNull()

    override fun getScriptSdk(virtualFile: VirtualFile): Sdk? = with(scriptDependenciesData.get()) {
        getProjectSdk()?.let { return it }
        val configurationWrapper = scriptDependenciesData.get().configurations[virtualFile]?.valueOrNull()
        return configurationWrapper?.javaHome?.let { sdks[it.toPath()] } ?: ProjectJdkTable.getInstance().allJdks.find { it.canBeUsedForScript() }
    }

    private fun getProjectSdk(): Sdk? {
        return ProjectRootManager.getInstance(project).projectSdk?.takeIf { it.canBeUsedForScript() }
    }

    private fun Sdk.hasValidClassPathRoots(): Boolean {
        val rootClasses = rootProvider.getFiles(OrderRootType.CLASSES)
        return rootClasses.isNotEmpty() && rootClasses.all { it.isValid }
    }

    private fun Sdk.canBeUsedForScript() = sdkType is JavaSdkType && hasValidClassPathRoots()

    override fun getScriptConfigurationResult(file: KtFile): ScriptCompilationConfigurationResult? =
        getConfiguration(file.alwaysVirtualFile)

    fun getConfiguration(virtualFile: VirtualFile): ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>? =
        scriptDependenciesData.get().configurations[virtualFile]

    private fun ScriptDependenciesData.getSdkSources(): List<VirtualFile> =
        sdks.flatMap { it.value.rootProvider.getFiles(OrderRootType.SOURCES).toList() }

    private fun ScriptDependenciesData.getSdkClasses(): List<VirtualFile> =
        sdks.flatMap { it.value.rootProvider.getFiles(OrderRootType.CLASSES).toList() }

    private val KtFile.alwaysVirtualFile: VirtualFile get() = originalFile.virtualFile ?: viewProvider.virtualFile

    companion object {
        fun getInstance(project: Project): ScriptConfigurationDataProvider =
            project.service<ScriptDependenciesProvider>() as ScriptConfigurationDataProvider

        fun getInstanceIfCreated(project: Project): ScriptConfigurationDataProvider? =
            project.serviceIfCreated<ScriptDependenciesProvider>() as? ScriptConfigurationDataProvider
    }
}

