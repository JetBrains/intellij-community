// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModule
import java.nio.file.Path

@ApiStatus.Internal
abstract class KaEntityBasedLibraryModuleBase<E : WorkspaceEntityWithSymbolicId, EID : SymbolicEntityId<E>>() :
  KaEntityBasedModule<E, EID>(), KaLibraryModule {

    override val binaryRoots: Collection<Path>
        get() = binaryVirtualFiles.mapNotNull { it.toNioPathOrNull() }

    override val baseContentScope: GlobalSearchScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaEntityBasedLibraryModuleScope(entityId, binaryVirtualFiles.toTypedArray(), project)
    }

    /**
     * The implementation may freely cache the result, as [KaLibrarySourceModule] is invalidated together with [KaLibraryModule].
     */
    abstract override val librarySources: KaLibrarySourceModule?

    override val directDependsOnDependencies: List<KaModule> get() = emptyList()
    override val directFriendDependencies: List<KaModule> get() = emptyList()
    override val directRegularDependencies: List<KaModule> get() = emptyList()

    override val transitiveDependsOnDependencies: List<KaModule> get() = emptyList()
}

