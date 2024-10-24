// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndex.ValueProcessor
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModuleStateModificationListener
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationListener
import org.jetbrains.kotlin.analysis.api.platform.utils.NullableConcurrentCache
import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.utils.errors.withKaModuleEntry
import org.jetbrains.kotlin.idea.base.indices.names.KotlinBinaryRootToPackageIndex
import org.jetbrains.kotlin.idea.base.indices.names.isSupportedByBinaryRootToPackageIndex
import org.jetbrains.kotlin.idea.base.projectStructure.openapiLibrary
import org.jetbrains.kotlin.idea.base.projectStructure.openapiSdk
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

/**
 * [IdeKotlinModulePackageNamesProvider] caches the results of [computePackageNames][org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider.computePackageNames]
 * for [KaModule]s in a project-wide cache.
 *
 * The package name sets computed and cached by this service may contain *false positives*, i.e. package names which aren't contained in the
 * module.
 *
 * Compared to caching the package names in the declaration provider itself, the project-wide service has the following advantage: the
 * cached package names only need to be invalidated if the module itself is modified. There is no need to invalidate cached package names
 * when a dependency module changes, like there is with regular session invalidation, because the set of package names in a module doesn't
 * depend on its dependencies. As such, cached package names can have a much longer lifetime than an LL FIR session for the same module.
 *
 * In addition to caching package names for modules, we cache them for binary roots separately. This is because multiple libraries may share
 * some binary roots. We compute package names per binary root, so it makes sense to cache it on this level as well.
 */
@Service(Service.Level.PROJECT)
internal class IdeKotlinModulePackageNamesProvider(private val project: Project) : Disposable {
    private val cache = NullableConcurrentCache<KaModule, Set<String>?>()

    private val binaryRootsCache = NullableConcurrentCache<String, Set<String>>()

    // Listeners are currently limited to library modules, as we don't compute package names for source modules yet. Hence, we don't listen
    // to out-of-block modification or source-only events at all.
    internal class ModuleStateModificationListener(val project: Project) : KotlinModuleStateModificationListener {
        override fun onModification(module: KaModule, modificationKind: KotlinModuleStateModificationKind) {
            getInstance(project).invalidate(module)
        }
    }

    internal class GlobalModuleStateModificationListener(val project: Project) : KotlinGlobalModuleStateModificationListener {
        override fun onModification() {
            getInstance(project).invalidateAll()
        }
    }

    init {
        LowMemoryWatcher.register(::invalidateAll, this)
    }

    fun computePackageNames(module: KaModule): Set<String>? =
        when (module) {
            is KaSourceModule -> computeSourceModulePackageSet(module)

            is KaLibraryModule ->
                cache.getOrPut(module) { module ->
                    module.binaryRootFiles?.let { computePackageSetFromBinaryRoots(it) }
                }

            is KaLibrarySourceModule -> computePackageNames(module.binaryLibrary)

            is KaBuiltinsModule -> StandardClassIds.builtInsPackages.mapTo(mutableSetOf()) { it.asString() }
            else -> null
        }

    private fun computeSourceModulePackageSet(module: KaSourceModule): Set<String>? = null // KTIJ-27450

    private fun computePackageSetFromBinaryRoots(binaryRoots: Collection<VirtualFile>): Set<String>? {
        if (binaryRoots.any { !it.isSupportedByBinaryRootToPackageIndex }) {
            return null
        }

        val packageNameSets = binaryRoots.map(::computePackageSetFromBinaryRoot)
        return when (packageNameSets.size) {
            0 -> emptySet()
            1 -> packageNameSets.single() // Don't build another set if there's only a single result.
            else -> packageNameSets.flattenTo(mutableSetOf())
        }
    }

    /**
     * We don't need to use the library module's scope, as the binary file name is already sufficiently specific (and false positives are
     * admissible). We don't even use an "all scope" because it still checks if the file is part of the project, which is unnecessary for
     * this use case.
     */
    private val binaryRootSearchScope = object : GlobalSearchScope(project) {
        override fun contains(file: VirtualFile): Boolean = true
        override fun isSearchInModuleContent(aModule: Module): Boolean = true
        override fun isSearchInLibraries(): Boolean = true
    }

    /**
     * If the [KotlinBinaryRootToPackageIndex] doesn't contain the binary root, we can still return an empty set, because the index is
     * exhaustive for binary libraries. An empty set means that the library doesn't contain any Kotlin declarations.
     */
    private fun computePackageSetFromBinaryRoot(binaryRoot: VirtualFile): Set<String> =
        binaryRootsCache.getOrPut(binaryRoot.name) { binaryRootName ->
            buildSet {
                FileBasedIndex.getInstance().processValues<String, String>(
                    KotlinBinaryRootToPackageIndex.NAME,
                    binaryRootName,
                    null,
                    ValueProcessor<String> { _, packageName ->
                        ProgressManager.checkCanceled()
                        add(packageName)
                        true
                    },
                    binaryRootSearchScope,
                )
            }
        }

    private fun invalidate(module: KaModule) {
        cache.map.remove(module)
        module.binaryRootFiles?.forEach { binaryRoot -> binaryRootsCache.map.remove(binaryRoot.name) }
    }

    private fun invalidateAll() {
        cache.map.clear()
        binaryRootsCache.map.clear()
    }

    private val KaModule.binaryRootFiles: Collection<VirtualFile>?
        get() = when (this) {
            is KaLibraryModule -> binaryVirtualFiles
            else -> null
        }

    override fun dispose() {
    }

    companion object {
        fun getInstance(project: Project): IdeKotlinModulePackageNamesProvider = project.service()
    }
}
