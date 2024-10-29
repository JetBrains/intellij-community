// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.scope.ModuleSourcesScope.SourceRootKind

/**
 * A scope which covers the production *or* test source roots of a module. It may be combined with other scopes into a
 * [CombinedSourceAndClassRootsScope].
 */
@ApiStatus.Internal
@Suppress("EqualsOrHashCode")
class ModuleSourcesScope(
    private val module: Module,
    private val sourceRootKind: SourceRootKind,
) : AbstractVirtualFileRootsScope(module.project), CombinableSourceAndClassRootsScope {
    /**
     * The kind of source roots covered by the [ModuleSourcesScope].
     */
    enum class SourceRootKind {
        PRODUCTION,
        TESTS,
    }

    override val roots: Object2IntMap<VirtualFile> = calculateRootsSet(module, sourceRootKind).toObject2IndexMap()

    override val modules: Set<Module> get() = setOf(module)

    override val includesLibraryRoots: Boolean get() = false

    override fun getFileRoot(file: VirtualFile): VirtualFile? = myProjectFileIndex.getModuleSourceOrLibraryClassesRoot(file)

    override fun isSearchInModuleContent(aModule: Module): Boolean = aModule == module

    override fun isSearchInLibraries(): Boolean = false

    override fun getDisplayName(): String = KotlinBaseProjectStructureBundle.message("module.sources.scope.0", module.name)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is ModuleSourcesScope) return false

        return module == other.module && sourceRootKind == other.sourceRootKind
    }

    override fun calcHashCode(): Int = sourceRootKind.hashCode() + 31 * module.hashCode()

    override fun toString(): String = "$sourceRootKind sources of module:${module.name}"

    companion object {
        fun production(module: Module): ModuleSourcesScope =
            ModuleSourcesScope(module, SourceRootKind.PRODUCTION)

        fun tests(module: Module): ModuleSourcesScope =
            ModuleSourcesScope(module, SourceRootKind.TESTS)
    }
}

private fun calculateRootsSet(module: Module, sourceRootKind: SourceRootKind): LinkedHashSet<VirtualFile> {
    val roots = LinkedHashSet<VirtualFile>()
    val moduleRootManager = ModuleRootManager.getInstance(module)

    for (contentEntry in moduleRootManager.contentEntries) {
        contentEntry
            .sourceFolders
            .filter { sourceFolder ->
                when (sourceRootKind) {
                    SourceRootKind.PRODUCTION -> !sourceFolder.isTestSource
                    SourceRootKind.TESTS -> sourceFolder.isTestSource
                }
            }
            .mapNotNullTo(roots) { it.file }
    }

    return roots
}
