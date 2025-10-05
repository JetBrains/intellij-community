// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.NonClasspathDirectoriesScope.compose
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

private class AllScriptsDependencies(
    val classes: Set<VirtualFile>,
    val sources: Set<VirtualFile>,
    val sdkClasses: Set<VirtualFile>,
    val sdkSources: Set<VirtualFile>,
)

private data class ScriptDependencies(
    val classes: Collection<VirtualFile>,
    val sources: Collection<VirtualFile>,
    val sdk: Sdk?,
) {
    companion object {
        val EMPTY = ScriptDependencies(setOf(), setOf(), null)
    }
}

class ScriptConfigurationsProviderImpl(project: Project, val coroutineScope: CoroutineScope) : ScriptConfigurationsProvider(project),
                                                                                               ScriptDependencyAware {

    private val allScriptsDependencies
        get() = ScriptDependenciesSingletonCache.getOrCompute(project) {
            val snapshot = project.workspaceModel.currentSnapshot

            val (classes, sources) = snapshot.entities(KotlinScriptLibraryEntity::class.java).toClassesSources()

            val (sdkClasses, sdkSources) =
                snapshot.entities(KotlinScriptEntity::class.java).mapNotNull { it.jdk }
                    .fold(mutableSetOf<VirtualFile>() to mutableSetOf<VirtualFile>()) { (accClasses, accSources), jdk ->
                        accClasses += jdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()
                        accSources += jdk.rootProvider.getFiles(OrderRootType.SOURCES).toList()
                        accClasses to accSources
                    }

            AllScriptsDependencies(classes, sources, sdkClasses, sdkSources)
        }

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> = allScriptsDependencies.classes

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope = with(allScriptsDependencies) {
        compose(classes.toList() + sdkClasses)
    }

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope = with(allScriptsDependencies) {
        compose(sources.toList() + sdkSources)
    }

    override fun getScriptDependenciesClassFilesScope(virtualFile: VirtualFile): GlobalSearchScope {
        val (classes, _, sdk) = virtualFile.currentDependencies
        val sdkClasses = sdk?.rootProvider?.getFiles(OrderRootType.CLASSES)?.toList() ?: emptyList<VirtualFile>()
        return compose(classes + sdkClasses)
    }

    override fun getScriptDependenciesClassFiles(virtualFile: VirtualFile): Collection<VirtualFile> =
        virtualFile.currentDependencies.classes

    override fun getScriptSdk(virtualFile: VirtualFile): Sdk? = virtualFile.currentDependencies.sdk

    private val VirtualFile.currentDependencies: ScriptDependencies
        get() {
            val snapshot = project.workspaceModel.currentSnapshot

            val virtualFileUrl = this.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
            val entity =
                snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl).filterIsInstance<KotlinScriptEntity>().firstOrNull()
                    ?: return ScriptDependencies.EMPTY

            val (classes, sources) = entity.dependencies.asSequence()
                .mapNotNull { snapshot.resolve(it) }
                .toClassesSources()

            return ScriptDependencies(
                classes, sources, entity.jdk
            )
        }

    private fun Sequence<KotlinScriptLibraryEntity>.toClassesSources(): Pair<MutableSet<VirtualFile>, MutableSet<VirtualFile>> =
        fold(mutableSetOf<VirtualFile>() to mutableSetOf<VirtualFile>()) { (accClasses, accSources), entity ->
            accClasses += entity.classes.mapNotNull { it.virtualFile }
            accSources += entity.sources.mapNotNull { it.virtualFile }
            accClasses to accSources
        }

    private val KotlinScriptEntity.jdk: Sdk?
        get() = when (val sdkDependency = sdk) {
            is SdkDependency -> ProjectJdkTable.getInstance().findJdk(sdkDependency.sdk.name, sdkDependency.sdk.type)
            is InheritedSdkDependency -> ProjectRootManager.getInstance(project).projectSdk
            else -> null
        }

    override fun getScriptConfigurationResult(file: KtFile): ScriptCompilationConfigurationResult? {
        val definition = file.findScriptDefinition() ?: return null
        return getConfigurationSupplier(definition).get(file.alwaysVirtualFile)?.scriptConfiguration
    }

    private fun getConfigurationSupplier(definition: ScriptDefinition): ScriptRefinedConfigurationResolver {
        return definition.compilationConfiguration[ScriptCompilationConfiguration.ide.configurationResolverDelegate]?.invoke()
            ?: DefaultScriptConfigurationHandler.getInstance(project)
    }

    companion object {
        fun getInstance(project: Project): ScriptConfigurationsProviderImpl =
            project.service<ScriptConfigurationsProvider>() as ScriptConfigurationsProviderImpl
    }
}

private object ScriptDependenciesSingletonCache {
    inline fun <reified T : Any> getOrCompute(project: Project, noinline compute: () -> T): T {
        val manager = CachedValuesManager.getManager(project)
        val key: Key<CachedValue<T>> = manager.getKeyForClass(T::class.java)
        val provider: CachedValueProvider<T> = CachedValueProvider {
            CachedValueProvider.Result.create(
                compute(), ScriptDependenciesModificationTracker.getInstance(project)
            )
        }
        return manager.getCachedValue(project, key, provider, false)
    }
}
