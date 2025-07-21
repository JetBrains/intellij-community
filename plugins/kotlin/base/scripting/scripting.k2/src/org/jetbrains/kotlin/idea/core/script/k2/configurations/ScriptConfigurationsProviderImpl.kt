// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

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
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.shared.ScriptClassPathUtil
import org.jetbrains.kotlin.idea.core.script.shared.ScriptVirtualFileCache
import org.jetbrains.kotlin.idea.core.script.shared.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.valueOrNull

private class ScriptDependenciesData(
    val classes: Set<VirtualFile> = mutableSetOf(),
    val sources: Set<VirtualFile> = mutableSetOf(),
    val sdks: Set<Sdk> = mutableSetOf(),
) {
    operator fun plus(other: ScriptDependenciesData): ScriptDependenciesData {
        return ScriptDependenciesData(
            this.classes + other.classes, this.sources + other.sources, this.sdks + other.sdks
        )
    }
}

class ScriptConfigurationsProviderImpl(project: Project, val coroutineScope: CoroutineScope) : ScriptConfigurationsProvider(project),
                                                                                               ScriptDependencyAware {
    private val allDependencies = AtomicReference(ScriptDependenciesData())

    fun store(configurations: Collection<ScriptConfigurationWithSdk>) {
        val cache = ScriptVirtualFileCache()

        val dataToAdd = configurations.fold(ScriptDependenciesData()) { left, right ->
            val configurationWrapper = right.scriptConfiguration.valueOrNull()

            if (configurationWrapper == null) {
                left
            } else {
                left + ScriptDependenciesData(
                    configurationWrapper.dependenciesClassPath.mapNotNull { cache.findVirtualFile(it.path) }.toSet(),
                    configurationWrapper.dependenciesSources.mapNotNull { cache.findVirtualFile(it.path) }.toSet(),
                    setOfNotNull(right.sdk)
                )
            }
        }
        allDependencies.accumulateAndGet(dataToAdd) { left, right -> left + right }
    }

    override fun getAllScriptDependenciesSources(): Collection<VirtualFile> = allDependencies.get()?.sources ?: emptyList()

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> = allDependencies.get().classes

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope = with(allDependencies.get()) {
        compose(classes.toList() + getSdkClasses())
    }

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope = with(allDependencies.get()) {
        compose(sources.toList() + getSdkSources())
    }

    override fun getScriptDependenciesClassFilesScope(virtualFile: VirtualFile): GlobalSearchScope {
        val (configuration, sdk) = getConfigurationWithSdk(virtualFile) ?: return GlobalSearchScope.EMPTY_SCOPE
        val configurationWrapper = configuration.valueOrNull() ?: return GlobalSearchScope.EMPTY_SCOPE

        val roots = configurationWrapper.dependenciesClassPath.mapNotNull { ScriptClassPathUtil.Companion.findVirtualFile(it.path) }

        val sdkClasses = sdk?.rootProvider?.getFiles(OrderRootType.CLASSES)?.toList() ?: emptyList<VirtualFile>()

        return compose(roots + sdkClasses)
    }

    override fun getScriptDependenciesClassFiles(virtualFile: VirtualFile): Collection<VirtualFile> {
        val dependencies =
            getConfigurationWithSdk(virtualFile)?.scriptConfiguration?.valueOrNull()?.dependenciesClassPath ?: return emptyList()
        return dependencies.mapNotNull { ScriptClassPathUtil.Companion.findVirtualFile(it.path) }
    }

    override fun getFirstScriptsSdk(): Sdk? = getProjectSdk() ?: allDependencies.get().sdks.firstOrNull()

    override fun getScriptSdk(virtualFile: VirtualFile): Sdk? =
        getConfigurationWithSdk(virtualFile)?.sdk ?: ProjectJdkTable.getInstance().allJdks.find { it.canBeUsedForScript() }

    override fun getScriptDependingOn(dependencies: Collection<String>): VirtualFile? = null

    private fun getProjectSdk(): Sdk? {
        return ProjectRootManager.getInstance(project).projectSdk?.takeIf { it.canBeUsedForScript() }
    }

    private fun Sdk.hasValidClassPathRoots(): Boolean {
        val rootClasses = rootProvider.getFiles(OrderRootType.CLASSES)
        return rootClasses.isNotEmpty() && rootClasses.all { it.isValid }
    }

    private fun Sdk.canBeUsedForScript() = sdkType is JavaSdkType && hasValidClassPathRoots()

    override fun getScriptConfigurationResult(file: KtFile): ScriptCompilationConfigurationResult? {
        val definition = file.findScriptDefinition() ?: return null
        return getConfigurationSupplier(definition).get(file.alwaysVirtualFile)?.scriptConfiguration
    }

    private fun getConfigurationSupplier(definition: ScriptDefinition): ScriptRefinedConfigurationResolver {
        return definition.compilationConfiguration[ScriptCompilationConfiguration.ide.configurationResolverDelegate]?.invoke()
            ?: DefaultScriptConfigurationHandler.getInstance(project)
    }

    fun getConfigurationWithSdk(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        val definition = findScriptDefinition(project, VirtualFileScriptSource(virtualFile))
        return getConfigurationSupplier(definition).get(virtualFile)
    }

    private fun ScriptDependenciesData.getSdkSources(): List<VirtualFile> =
        sdks.flatMap { it.rootProvider.getFiles(OrderRootType.SOURCES).toList() }

    private fun ScriptDependenciesData.getSdkClasses(): List<VirtualFile> =
        sdks.flatMap { it.rootProvider.getFiles(OrderRootType.CLASSES).toList() }

    companion object {
        fun getInstance(project: Project): ScriptConfigurationsProviderImpl =
            project.service<ScriptConfigurationsProvider>() as ScriptConfigurationsProviderImpl

        fun getInstanceIfCreated(project: Project): ScriptConfigurationsProviderImpl? =
            project.serviceIfCreated<ScriptConfigurationsProvider>() as? ScriptConfigurationsProviderImpl
    }
}
