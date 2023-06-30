// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

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
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.kotlin.analysis.project.structure.KtBuiltinsModule
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptDependencyModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptModule
import org.jetbrains.kotlin.analysis.project.structure.KtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.impl.KotlinModuleDependentsProviderBase
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * [IdeKotlinModuleDependentsProvider] provides [KtModule] dependents by querying the workspace model.
 */
internal class IdeKotlinModuleDependentsProvider(private val project: Project) : KotlinModuleDependentsProviderBase() {
    override fun getDirectDependents(module: KtModule): Set<KtModule> {
        val symbolicId = when (module) {
            is KtSourceModuleByModuleInfo -> module.moduleId
            is KtLibraryModuleByModuleInfo -> module.libraryId ?: return emptySet()
            is KtLibrarySourceModuleByModuleInfo -> return getDirectDependents(module.binaryLibrary)

            // No dependents need to be provided for `KtSdkModule` and `KtBuiltinsModule` (see `KotlinModuleDependentsProvider`).
            is KtSdkModule -> return emptySet()
            is KtBuiltinsModule -> return emptySet()

            // Script modules are not supported yet (see KTIJ-25620).
            is KtScriptModule, is KtScriptDependencyModule -> return emptySet()

            is NotUnderContentRootModuleByModuleInfo -> return emptySet()

            else -> throw KotlinExceptionWithAttachments("Unexpected ${KtModule::class.simpleName}").withAttachment("module.txt", module)
        }

        val directDependents = mutableSetOf<KtModule>()
        directDependents.addFriendDependents(module)
        directDependents.addWorkspaceModelDependents(symbolicId)

        return directDependents
    }

    private fun MutableSet<KtModule>.addFriendDependents(module: KtModule) {
        // The only friend dependency that currently exists in the IDE is the dependency of an IDEA module's test sources on its production
        // sources. Hence, a test source `KtModule` is a direct dependent of its production source `KtModule`.
        if (module is KtSourceModuleByModuleInfo && module.ideaModuleInfo is ModuleProductionSourceInfo) {
            addIfNotNull(module.ideaModule.testSourceInfo?.toKtModule())
        }
    }

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
