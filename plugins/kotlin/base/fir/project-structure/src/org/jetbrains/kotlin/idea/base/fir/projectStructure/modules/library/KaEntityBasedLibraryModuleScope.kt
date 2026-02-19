// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.LibraryScopeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.SymbolicEntityId
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
class KaEntityBasedLibraryModuleScope(
    private val entityId: SymbolicEntityId<*>,
    classesRoots: Array<VirtualFile>,
    project: Project,
) : LibraryScopeBase(project, classesRoots, /* sources = */VirtualFile.EMPTY_ARRAY), CombinableSourceAndClassRootsScope {
    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getClassRootForFile(file)

    override val roots: Object2IntMap<VirtualFile> get() = entries
    override val modules: Set<Module> get() = emptySet()
    override val includesLibraryClassRoots: Boolean get() = true
    override val includesLibrarySourceRoots: Boolean get() = false

    override fun equals(other: Any?): Boolean = this === other || other is KaEntityBasedLibraryModuleScope && entityId == other.entityId
    override fun calcHashCode(): Int = entityId.hashCode()
    override fun toString(): String = "KaLibraryModuleScope($entityId)"
}