// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.ApiStatus

/**
 * A scope which combines multiple [CombinableSourceAndClassRootsScope]s into a single, efficient root-based scope. Combining multiple
 * root-based scopes such as [ModuleSourcesScope] is beneficial as the roots of these scopes only need to be combined into a single hash
 * map.
 *
 * While the scope supports library *classes*, it does not support library *sources*, because we'd need to add another check to [contains]
 * which is unnecessary for source scopes. Combining library source scopes is not necessary because we don't have library sources as
 * dependencies, and don't usually want to search in multiple library sources at once.
 *
 * @param modules Purely used for implementing [isSearchInModuleContent]. The scope may be wider than just the source content of the given
 *  modules, e.g. with library classes.
 */
@ApiStatus.Internal
@Suppress("EqualsOrHashCode")
class CombinedSourceAndClassRootsScope private constructor(
    override val roots: Object2IntMap<VirtualFile>,
    override val modules: Set<Module>,
    override val includesLibraryRoots: Boolean,
    project: Project,
) : AbstractVirtualFileRootsScope(project), CombinableSourceAndClassRootsScope {
    override fun getFileRoot(file: VirtualFile): VirtualFile? = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file)

    override fun isSearchInModuleContent(aModule: Module): Boolean = aModule in modules

    override fun isSearchInLibraries(): Boolean = includesLibraryRoots

    override fun computeFileEnumeration(): VirtualFileEnumeration? {
        // Extracting virtual file enumerations for combined scopes is expensive and leads to a degradation in many cases.
        return null
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is CombinedSourceAndClassRootsScope && roots == other.roots

    override fun calcHashCode(): Int = roots.hashCode()

    override fun toString(): String = "Combined source and class roots scope: ${roots}"

    companion object {
        fun create(scopes: List<CombinableSourceAndClassRootsScope>, project: Project): GlobalSearchScope {
            if (scopes.isEmpty()) return EMPTY_SCOPE

            val roots = computeRoots(scopes)
            val modules = scopes.flatMapTo(mutableSetOf()) { it.modules }.ifEmpty { emptySet() }
            val includesLibraryRoots = scopes.any { it.includesLibraryRoots }

            return CombinedSourceAndClassRootsScope(roots, modules, includesLibraryRoots, project)
        }

        private fun computeRoots(scopes: List<CombinableSourceAndClassRootsScope>): Object2IntMap<VirtualFile> =
            scopes.flatMapTo(LinkedHashSet<VirtualFile>()) { it.getOrderedRoots() }.toObject2IndexMap()
    }
}
