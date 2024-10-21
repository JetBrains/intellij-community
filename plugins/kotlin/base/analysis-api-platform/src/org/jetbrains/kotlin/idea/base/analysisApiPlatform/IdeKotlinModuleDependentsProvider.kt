// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleDependentsProviderBase
import org.jetbrains.kotlin.analysis.api.projectStructure.*
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * [IdeKotlinModuleDependentsProvider] provides [KaModule] dependents by querying the workspace model and Kotlin plugin indices/caches.
 */
@ApiStatus.Internal
abstract class IdeKotlinModuleDependentsProvider(protected val project: Project) : KotlinModuleDependentsProviderBase() {
    override fun getDirectDependents(module: KaModule): Set<KaModule> {
        return when (module) {
            is KaSourceModule -> getDirectDependentsForSourceModule(module)
            is KaLibraryModule -> {
                if (module.isSdk) {
                    // No dependents need to be provided for SDK modules and `KaBuiltinsModule` (see `KotlinModuleDependentsProvider`).
                    return emptySet()
                }
                getDirectDependentsForLibraryNonSdkModule(module)
            }
            is KaLibrarySourceModule -> getDirectDependents(module.binaryLibrary)

            is KaBuiltinsModule -> emptySet()

            // There is no way to find dependents of danging file modules, as such modules are created on-site.
            is KaDanglingFileModule -> emptySet()

            // Script modules are not supported yet (see KTIJ-25620).
            is KaScriptModule, is KaScriptDependencyModule -> emptySet()
            is KaNotUnderContentRootModule -> emptySet()

            else -> throw KotlinExceptionWithAttachments("Unexpected ${module::class.simpleName}").withAttachment("module.txt", module)
        }
    }

    private fun getDirectDependentsForSourceModule(module: KaSourceModule): Set<KaModule> =
        buildSet {
            addFriendDependentsForSourceModule(module)
            addWorkspaceModelDependents(module.symbolicId)
            addAnchorModuleDependents(module, this)
        }

    private fun MutableSet<KaModule>.addFriendDependentsForSourceModule(module: KaSourceModule) {
        // The only friend dependency that currently exists in the IDE is the dependency of an IDEA module's test sources on its production
        // sources. Hence, a test source `KaModule` is a direct dependent of its production source `KaModule`.
        if (module.sourceModuleKind == KaSourceModuleKind.PRODUCTION) {
            addIfNotNull(module.openapiModule.toKaSourceModuleForTest())
        }
    }

    protected abstract fun addAnchorModuleDependents(module: KaSourceModule, to: MutableSet<KaModule>)

    protected abstract fun getDirectDependentsForLibraryNonSdkModule(module: KaLibraryModule): Set<KaModule>

    private fun MutableSet<KaModule>.addWorkspaceModelDependents(symbolicId: SymbolicEntityId<WorkspaceEntityWithSymbolicId>) {
        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        snapshot
            .referrers(symbolicId, ModuleEntity::class.java)
            .forEach { moduleEntity ->
                // The set of dependents should not include `module` itself.
                if (moduleEntity.symbolicId == symbolicId) return@forEach

                // We can skip the module entity if `findModule` returns `null` because the module won't have been added to the project
                // model yet and thus cannot be a proper `KaModule`. If there is a production source `KaModule`, we only need to add that
                // because the test source `KaModule` will be a direct friend dependent of the production source `KaModule`.
                addIfNotNull(moduleEntity.symbolicId.toKaSourceModuleForProductionOrTest(project))
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
    private val transitiveDependentsCache: CachedValue<Cache<KaModule, Set<KaModule>>> =
        CachedValuesManager.getManager(project).createCachedValue {
            CachedValueProvider.Result.create(
                Caffeine.newBuilder().maximumSize(100).build(),
                ProjectRootModificationTracker.getInstance(project),
            )
        }

    override fun getTransitiveDependents(module: KaModule): Set<KaModule> =
        transitiveDependentsCache.value.get(module) {
            // The computation does not reuse sub-results that may already have been cached because transitive dependents are usually only
            // computed for select modules, so the performance impact of this computation is expected to be negligible.
            computeTransitiveDependents(it)
        }

    @OptIn(K1ModeProjectStructureApi::class)
    override fun getRefinementDependents(module: KaModule): Set<KaModule> {
        if (module !is KaSourceModule) return emptySet()
        val implementingModules = module.openapiModule.implementingModules
        return implementingModules.mapNotNullTo(mutableSetOf()) { it.toKaSourceModuleForProductionOrTest() }.ifEmpty { emptySet() }
    }
}
