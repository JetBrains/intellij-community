// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.kotlin.analysis.project.structure.KtBuiltinsModule
import org.jetbrains.kotlin.analysis.project.structure.KtDanglingFileModule
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptDependencyModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptModule
import org.jetbrains.kotlin.analysis.project.structure.KtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.impl.KotlinModuleDependentsProviderBase
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.util.getTransitiveLibraryDependencyInfos
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * [IdeKotlinModuleDependentsProvider] provides [KtModule] dependents by querying the workspace model and Kotlin plugin indices/caches.
 */
internal class IdeKotlinModuleDependentsProvider(private val project: Project) : KotlinModuleDependentsProviderBase() {
    override fun getDirectDependents(module: KtModule): Set<KtModule> {
        return when (module) {
            is KtSourceModuleByModuleInfo -> getDirectDependentsForSourceModule(module)
            is KtLibraryModuleByModuleInfo -> getDirectDependentsForLibraryModule(module)
            is KtLibrarySourceModuleByModuleInfo -> getDirectDependents(module.binaryLibrary)

            // No dependents need to be provided for `KtSdkModule` and `KtBuiltinsModule` (see `KotlinModuleDependentsProvider`).
            is KtSdkModule -> emptySet()
            is KtBuiltinsModule -> emptySet()

            // There is no way to find dependents of danging file modules, as such modules are created on-site.
            is KtDanglingFileModule -> emptySet()

            // Script modules are not supported yet (see KTIJ-25620).
            is KtScriptModule, is KtScriptDependencyModule -> emptySet()
            is NotUnderContentRootModuleByModuleInfo -> emptySet()

            else -> throw KotlinExceptionWithAttachments("Unexpected ${KtModule::class.simpleName}").withAttachment("module.txt", module)
        }
    }

    private fun getDirectDependentsForSourceModule(module: KtSourceModuleByModuleInfo): Set<KtModule> =
        mutableSetOf<KtModule>().apply {
            addFriendDependentsForSourceModule(module)
            addWorkspaceModelDependents(module.moduleId)
            addAnchorModuleDependents(module)
        }

    private fun MutableSet<KtModule>.addFriendDependentsForSourceModule(module: KtSourceModuleByModuleInfo) {
        // The only friend dependency that currently exists in the IDE is the dependency of an IDEA module's test sources on its production
        // sources. Hence, a test source `KtModule` is a direct dependent of its production source `KtModule`.
        if (module.ideaModuleInfo is ModuleProductionSourceInfo) {
            addIfNotNull(module.ideaModule.testSourceInfo?.toKtModule())
        }
    }

    private fun MutableSet<KtModule>.addAnchorModuleDependents(module: KtSourceModuleByModuleInfo) {
        val moduleInfo = module.ideaModuleInfo as? ModuleSourceInfo ?: return

        // If `module` is an anchor module, it has library dependents in the form of anchoring libraries. See
        // `ResolutionAnchorCacheService` for additional documentation.
        val anchoringLibraries = ResolutionAnchorCacheService.getInstance(project).librariesForResolutionAnchors[moduleInfo] ?: return

        // Because dependency relationships between libraries aren't supported by the project model (as noted in
        // `ResolutionAnchorCacheService`), library dependencies are approximated by the following relationship: If a module `M1` depends on
        // two libraries `L1` and `L2`, `L1` depends on `L2` and `L2` depends on `L1` (`L1 <--> L2`). This does not apply without
        // restriction for multi-platform projects. However, anchor module usage is strictly limited to the `intellij` project, which is not
        // a multi-platform project. Because the approximate library dependencies are bidirectional, library dependencies are also library
        // dependents, and we can simply use `getTransitiveLibraryDependencyInfos`.
        //
        // Because anchor modules are rare and `getTransitiveDependents` already caches dependents as a whole, there is currently no need to
        // cache these transitive library dependencies.
        LibraryDependenciesCache.getInstance(project)
            .getTransitiveLibraryDependencyInfos(anchoringLibraries)
            .forEach { libraryInfo ->
                add(libraryInfo.toKtModule())
                add(libraryInfo.sourcesModuleInfo.toKtModule())
            }
    }

    private fun getDirectDependentsForLibraryModule(module: KtLibraryModuleByModuleInfo): Set<KtModule> =
        project.service<LibraryUsageIndex>()
            .getDependentModules(module.libraryInfo)
            .mapNotNullTo(mutableSetOf()) { it.productionOrTestSourceModuleInfo?.toKtModule() }

    private fun MutableSet<KtModule>.addWorkspaceModelDependents(symbolicId: SymbolicEntityId<WorkspaceEntityWithSymbolicId>) {
        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        snapshot
            .referrers(symbolicId, ModuleEntity::class.java)
            .forEach { moduleEntity ->
                // The set of dependents should not include `module` itself.
                if (moduleEntity.symbolicId == symbolicId) return@forEach

                // We can skip the module entity if `findModule` returns `null` because the module won't have been added to the project
                // model yet and thus cannot be a proper `KtModule`. If there is a production source `KtModule`, we only need to add that
                // because the test source `KtModule` will be a direct friend dependent of the production source `KtModule`.
                addIfNotNull(moduleEntity.findModule(snapshot)?.productionOrTestSourceModuleInfo?.toKtModule())
            }
    }

    /**
     * Caching transitive dependents is crucial. [getTransitiveDependents] will frequently be called by session invalidation when typing in
     * a Kotlin file. Large projects might have core modules with over a hundred or even a thousand transitive dependents. At the same time,
     * we can keep the size of this cache small, because transitive dependents will usually only be requested for a single module (e.g. the
     * module to be invalidated after an out-of-block modification).
     *
     * The timing of invalidation is important, since the [IdeKotlinModuleDependentsProvider] may be used in workspace model listeners when
     * project structure changes. Using a *before change* workspace model listener is not an option, because we'd have to guarantee that
     * this listener is placed after all other listeners which might use `IdeKotlinModuleDependentsProvider`. It's not entirely impossible
     * due to the existence of `Fe10/FirOrderedWorkspaceModelChangeListener`, but a simpler solution such as the project root modification
     * tracker, which is incremented after *before change* events have been handled, seems preferable.
     */
    private val transitiveDependentsCache: CachedValue<Cache<KtModule, Set<KtModule>>> = CachedValuesManager.getManager(project).createCachedValue {
        CachedValueProvider.Result.create(
            Caffeine.newBuilder().maximumSize(100).build(),
            ProjectRootModificationTracker.getInstance(project),
        )
    }

    override fun getTransitiveDependents(module: KtModule): Set<KtModule> =
        transitiveDependentsCache.value.get(module) {
            // The computation does not reuse sub-results that may already have been cached because transitive dependents are usually only
            // computed for select modules, so the performance impact of this computation is expected to be negligible.
            computeTransitiveDependents(it)
        }
}
