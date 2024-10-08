// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.psi.search.impl.VirtualFileEnumerationAware
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.ApiStatus

/**
 * A base implementation of scopes based on a single virtual file roots map, such as [ModuleSourcesScope].
 *
 * While [ModuleSourcesScope] is currently the only implementation, this class can also be used as a base for
 * `ModulesWithDependenciesScope` once [ModuleSourcesScope] is migrated to the platform.
 */
@ApiStatus.Internal
abstract class AbstractVirtualFileRootsScope(project: Project) : GlobalSearchScope(project), VirtualFileEnumerationAware {
    @Volatile
    private var virtualFileEnumeration: VirtualFileEnumeration? = null

    @Volatile
    private var vfsModificationCount: Long = 0

    protected val myProjectFileIndex: ProjectFileIndexImpl = ProjectRootManager.getInstance(project).fileIndex as ProjectFileIndexImpl

    /**
     * A map from [VirtualFile] roots to an integer which represents the position of the root in the classpath.
     */
    protected abstract val roots: Object2IntMap<VirtualFile>

    protected abstract fun getFileRoot(file: VirtualFile): VirtualFile?

    override fun contains(file: VirtualFile): Boolean {
        // Note: The scope's logic is copied from `ModuleWithDependenciesScope`, which has an additional check for Bazel single-file
        // modules. The check in `ModuleWithDependenciesScope` is actually a remnant of a failed experiment, so omitting the check here is
        // fine.
        val root = getFileRoot(file) ?: return false
        return roots.containsKey(root)
    }

    override fun compare(file1: VirtualFile, file2: VirtualFile): Int {
        val r1 = getFileRoot(file1)
        val r2 = getFileRoot(file2)
        if (Comparing.equal(r1, r2)) return 0

        if (r1 == null) return -1
        if (r2 == null) return 1

        val roots = roots
        val i1 = roots.getInt(r1)
        val i2 = roots.getInt(r2)
        if (i1 == 0 && i2 == 0) return 0
        if (i1 > 0 && i2 > 0) return i2 - i1
        return if (i1 > 0) 1 else -1
    }

    override fun extractFileEnumeration(): VirtualFileEnumeration? {
        val currentVfsStamp = VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.modificationCount
        if (currentVfsStamp != vfsModificationCount) {
            virtualFileEnumeration = computeFileEnumeration()
            vfsModificationCount = currentVfsStamp
        }
        return virtualFileEnumeration.takeIf { it != VirtualFileEnumeration.EMPTY }
    }

    protected open fun computeFileEnumeration(): VirtualFileEnumeration? = computeFileEnumerationUnderRoots(roots.keys)
}
