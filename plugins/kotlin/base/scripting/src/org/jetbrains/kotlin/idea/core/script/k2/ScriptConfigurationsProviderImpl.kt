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
import org.jetbrains.kotlin.idea.core.script.SCRIPT_CONFIGURATIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.valueOrNull

private class ScriptDependenciesData(
    val classes: Set<VirtualFile> = mutableSetOf(),
    val sources: Set<VirtualFile> = mutableSetOf(),
    val sdks: Map<Path, Sdk> = mutableMapOf(),
)

class ScriptConfigurationsProviderImpl(project: Project) : ScriptConfigurationsProvider(project), ScriptDependencyAware {
    private val allDependencies = AtomicReference(ScriptDependenciesData())
    private val dependenciesSourceByDefinition = AtomicReference(mapOf<String, ScriptConfigurationsSource<*>>())

    init {
        notifySourceUpdated()
    }

    fun notifySourceUpdated() {
        val configurationSources = SCRIPT_CONFIGURATIONS_SOURCES.getExtensions(project)

        val allScriptsSources = mutableSetOf<VirtualFile>()
        val allScriptClasses = mutableSetOf<VirtualFile>()
        val allScriptsSdks = mutableMapOf<Path, Sdk>()

        val sourceByDefinition = mutableMapOf<String, ScriptConfigurationsSource<*>>()

        configurationSources.forEach { source ->
            val data = source.data.get()
            val configurations = data.configurations.values.mapNotNull { it.valueOrNull() }

            configurations.forEach {
                allScriptsSources.addAll(toVfsRoots(it.dependenciesSources))
                allScriptClasses.addAll(toVfsRoots(it.dependenciesClassPath))
            }

            allScriptsSdks.putAll(data.sdks)

            val definitions = source.getScriptDefinitionsSource()?.definitions
            definitions?.forEach { sourceByDefinition.put(it.definitionId, source) }
        }

        dependenciesSourceByDefinition.set(sourceByDefinition)
        allDependencies.set(ScriptDependenciesData(allScriptClasses, allScriptsSources))
    }

    override fun getAllScriptDependenciesSources(): Collection<VirtualFile> =
        allDependencies.get()?.sources ?: emptyList()

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> =
        allDependencies.get().classes

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope =
        with(allDependencies.get()) {
            compose(classes.toList() + getSdkClasses())
        }

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope =
        with(allDependencies.get()) {
            compose(sources.toList() + getSdkSources())
        }

    override fun getScriptDependenciesClassFilesScope(virtualFile: VirtualFile): GlobalSearchScope {
        val data = getConfigurationsSourceData(virtualFile) ?: return GlobalSearchScope.EMPTY_SCOPE

        val configurationWrapper = data.configurations[virtualFile]?.valueOrNull()
            ?: return GlobalSearchScope.EMPTY_SCOPE

        val roots = toVfsRoots(configurationWrapper.dependenciesClassPath)

        val sdk = configurationWrapper.javaHome?.let { data.sdks[it.toPath()] }
        val sdkClasses = sdk?.rootProvider?.getFiles(OrderRootType.CLASSES)?.toList() ?: emptyList<VirtualFile>()

        return compose(roots + sdkClasses)
    }

    override fun getScriptDependenciesClassFiles(virtualFile: VirtualFile): Collection<VirtualFile> {
        val dependencies =
            getConfigurationsSourceData(virtualFile)?.configurations[virtualFile]?.valueOrNull()?.dependenciesClassPath
                ?: return emptyList()
        return toVfsRoots(dependencies)
    }

    override fun getFirstScriptsSdk(): Sdk? =
        getProjectSdk() ?: allDependencies.get().sdks.values.firstOrNull()

    override fun getScriptSdk(virtualFile: VirtualFile): Sdk? {
        val data = getConfigurationsSourceData(virtualFile)

        val configurationWrapper = data?.configurations[virtualFile]?.valueOrNull()
        return configurationWrapper?.javaHome?.let { data.sdks[it.toPath()] }
            ?: ProjectJdkTable.getInstance().allJdks.find { it.canBeUsedForScript() }
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
        getScriptConfigurationResult(file.alwaysVirtualFile)

    fun getScriptConfigurationResult(virtualFile: VirtualFile): ScriptCompilationConfigurationResult? =
        getConfigurationsSource(virtualFile)?.getScriptConfigurations(virtualFile)

    fun getConfigurationsSource(virtualFile: VirtualFile): ScriptConfigurationsSource<*>? {
        val definition = virtualFile.findScriptDefinition(project)
        return dependenciesSourceByDefinition.get()[definition?.definitionId] ?: project.scriptConfigurationsSourceOfType<DependentScriptConfigurationsSource>()
    }

    private fun getConfigurationsSourceData(virtualFile: VirtualFile): ScriptConfigurations? =
        getConfigurationsSource(virtualFile)?.data?.get()

    private fun ScriptDependenciesData.getSdkSources(): List<VirtualFile> =
        sdks.flatMap { it.value.rootProvider.getFiles(OrderRootType.SOURCES).toList() }

    private fun ScriptDependenciesData.getSdkClasses(): List<VirtualFile> =
        sdks.flatMap { it.value.rootProvider.getFiles(OrderRootType.CLASSES).toList() }

    private val KtFile.alwaysVirtualFile: VirtualFile get() = originalFile.virtualFile ?: viewProvider.virtualFile

    companion object {
        fun getInstance(project: Project): ScriptConfigurationsProviderImpl =
            project.service<ScriptConfigurationsProvider>() as ScriptConfigurationsProviderImpl

        fun getInstanceIfCreated(project: Project): ScriptConfigurationsProviderImpl? =
            project.serviceIfCreated<ScriptConfigurationsProvider>() as? ScriptConfigurationsProviderImpl
    }
}

