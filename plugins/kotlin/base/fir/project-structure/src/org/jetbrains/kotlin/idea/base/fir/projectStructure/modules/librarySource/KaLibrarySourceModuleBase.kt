// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.LibraryScopeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.psi.search.GlobalSearchScope
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleBase
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryModuleBase
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope
import org.jetbrains.kotlin.platform.TargetPlatform

@ApiStatus.Internal
abstract class KaLibrarySourceModuleBase : KaLibrarySourceModule, KaModuleBase() {
    abstract override val binaryLibrary: KaLibraryModuleBase<*, *>

    override val libraryName: String get() = binaryLibrary.libraryName

    // Library source dependencies should mirror the backing binary library module's dependencies.
    override val directRegularDependencies: List<KaModule> get() = binaryLibrary.directRegularDependencies
    override val directFriendDependencies: List<KaModule> get() = binaryLibrary.directFriendDependencies
    override val directDependsOnDependencies: List<KaModule> get() = binaryLibrary.directDependsOnDependencies
    override val transitiveDependsOnDependencies: List<KaModule> get() = binaryLibrary.transitiveDependsOnDependencies

    override val targetPlatform: TargetPlatform get() = binaryLibrary.targetPlatform
    override val project: Project get() = binaryLibrary.project

    override val baseContentScope: GlobalSearchScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaLibrarySourceScope(binaryLibrary.entityId, sourceRoots, project)
    }

    protected abstract val sourceRoots: Array<VirtualFile>

    abstract override fun hashCode(): Int

    abstract override fun equals(other: Any?): Boolean

    override fun toString(): String {
        return "${this::class.simpleName} for $binaryLibrary"
    }
}

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
private class KaLibrarySourceScope(
    private val entityId: SymbolicEntityId<*>,
    sourceRoots: Array<VirtualFile>,
    project: Project,
) : LibraryScopeBase(project, /* classes = */ VirtualFile.EMPTY_ARRAY, sourceRoots), CombinableSourceAndClassRootsScope {
    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getSourceRootForFile(file)

    override val roots: Object2IntMap<VirtualFile> get() = entries
    override val modules: Set<Module> get() = emptySet()

    override val includesLibraryClassRoots: Boolean get() = false
    override val includesLibrarySourceRoots: Boolean get() = true

    override fun equals(other: Any?): Boolean = this === other || other is KaLibrarySourceScope && entityId == other.entityId
    override fun calcHashCode(): Int = entityId.hashCode()
    override fun toString(): String = "KaLibrarySourceScope($entityId)"
}