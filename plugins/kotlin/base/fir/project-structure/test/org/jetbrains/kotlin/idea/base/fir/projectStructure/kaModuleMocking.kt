// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModuleCreationData
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source.KaSourceModuleImpl
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.InternalKaModuleConstructor
import org.jetbrains.kotlin.idea.base.fir.projectStructure.provider.K2IDEProjectStructureProviderCache
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleWithKind

/**
 * Allows registering a custom [KaSourceModuleWithKind] under the given [moduleId] with the project structure provider.
 *
 * Internally, mocking is supported by `K2IDEProjectStructureProviderCache`. As such, mocking won't work during modification event handling
 * because the cache doesn't return cached elements during that period. Furthermore, registered mocks will be subject to invalidation of the
 * project structure provider cache.
 */
@ApiStatus.Internal
fun KaSourceModuleWithKind.registerAsMock(moduleId: ModuleId) {
    val cache = K2IDEProjectStructureProviderCache.getInstance(project)
    cache.registerSourceModule(moduleId, this)
}

/**
 * Checks if the given [KaSourceModule] is a custom source module created with [createKaSourceModuleWithCustomBaseContentScope].
 */
@ApiStatus.Internal
fun KaModule.isCustomSourceModule(): Boolean = this is KaSourceModuleWithCustomBaseContentScope

/**
 * Allows creating a [KaSourceModuleWithKind] with a custom base content scope for use with [registerAsMock].
 */
@ApiStatus.Internal
fun createKaSourceModuleWithCustomBaseContentScope(
    moduleId: ModuleId,
    kind: KaSourceModuleKind,
    project: Project,
    getScope: () -> GlobalSearchScope,
): KaSourceModuleWithKind = KaSourceModuleWithCustomBaseContentScope(moduleId, kind, project, getScope)

@OptIn(InternalKaModuleConstructor::class)
private class KaSourceModuleWithCustomBaseContentScope(
    moduleId: ModuleId,
    kind: KaSourceModuleKind,
    project: Project,
    private val getScope: () -> GlobalSearchScope,
) : KaSourceModuleImpl(moduleId, kind, project, KaEntityBasedModuleCreationData(false, 0, 0)) {
    override val baseContentScope: GlobalSearchScope
        get() = getScope()
}
