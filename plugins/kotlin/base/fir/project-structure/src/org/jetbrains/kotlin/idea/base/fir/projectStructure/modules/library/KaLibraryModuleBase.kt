// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.LibraryScopeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.psi.search.GlobalSearchScope
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.impl.base.projectStructure.KaBuiltinsModuleImpl
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModule
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope
import java.nio.file.Path

@ApiStatus.Internal
abstract class KaLibraryModuleBase<E : WorkspaceEntityWithSymbolicId, EID : SymbolicEntityId<E>>() :
  KaEntityBasedModule<E, EID>(), KaLibraryModule {

    override val binaryRoots: Collection<Path>
        get() = binaryVirtualFiles.mapNotNull { it.toNioPathOrNull() }

    override val baseContentScope: GlobalSearchScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaLibraryModuleScope(entityId, binaryVirtualFiles.toTypedArray(), project)
    }

    /**
     * The implementation may freely cache the result, as [KaLibrarySourceModule] is invalidated together with [KaLibraryModule].
     */
    abstract override val librarySources: KaLibrarySourceModule?

    override val directDependsOnDependencies: List<KaModule> get() = emptyList()
    override val directFriendDependencies: List<KaModule> get() = emptyList()

    @OptIn(KaImplementationDetail::class)
    override val directRegularDependencies: List<KaModule>
        // should be empty, mitigation of KT-74010
        get() = listOf(KaBuiltinsModuleImpl(targetPlatform, project))

    override val transitiveDependsOnDependencies: List<KaModule> get() = emptyList()

}

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
private class KaLibraryModuleScope(
    private val entityId: SymbolicEntityId<*>,
    classesRoots: Array<VirtualFile>,
    project: Project,
) : LibraryScopeBase(project, classesRoots, /* sources = */VirtualFile.EMPTY_ARRAY), CombinableSourceAndClassRootsScope {
    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getClassRootForFile(file)

    override val roots: Object2IntMap<VirtualFile> get() = entries
    override val modules: Set<Module> get() = emptySet()
    override val includesLibraryClassRoots: Boolean get() = true
    override val includesLibrarySourceRoots: Boolean get() = false

    override fun equals(other: Any?): Boolean = this === other || other is KaLibraryModuleScope && entityId == other.entityId
    override fun calcHashCode(): Int = entityId.hashCode()
    override fun toString(): String = "KaLibraryModuleScope($entityId)"
}