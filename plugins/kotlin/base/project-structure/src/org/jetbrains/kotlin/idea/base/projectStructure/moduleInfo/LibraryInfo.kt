// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.PathUtil
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.kotlin.analyzer.LibraryModuleInfo
import org.jetbrains.kotlin.analyzer.TrackableModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinModificationTrackerProvider
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.useLibraryToSourceAnalysis
import org.jetbrains.kotlin.idea.base.projectStructure.scope.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices

/**
 * [LibraryInfo] is the Kotlin plugin's view on a [LibraryEx].
 *
 * Multiple different [LibraryEx]s with the same content may be mapped to a single [LibraryInfo]. For example, two different module-level
 * libraries with the same roots will have the same [LibraryInfo]. Such libraries are called *deduplicated*. The deduplication is performed
 * by [LibraryInfoCache]. This avoids keeping multiple, duplicate cache entries for [LibraryEx]s with effectively the same content.
 *
 * The dependencies and dependents of a [LibraryInfo] are calculated from *all* [LibraryEx]s associated with it. In that sense, a
 * [LibraryInfo] is a *collection* of [LibraryEx]s.
 *
 * @param library The *anchor library* which acts as the content reference for the [LibraryInfo]. It is sometimes used as a cache key in
 *  deduplication contexts. It is *not* necessarily the only [LibraryEx] associated with this [LibraryInfo].
 *
 * @see LibraryInfoCache
 */
abstract class LibraryInfo internal constructor(
    override val project: Project,
    val library: LibraryEx,
) : IdeaModuleInfo, LibraryModuleInfo, BinaryModuleInfo, TrackableModuleInfo {
    private val topClassesPackageNames: Set<String>?
    private val classesEntriesVirtualFileSystems: Set<NewVirtualFileSystem>?

    private val topSourcesPackageNames: Set<String>?
    private val sourcesEntriesVirtualFileSystems: Set<NewVirtualFileSystem>?

    init {
        val (classes, sources) =
            runReadAction {
                library.getFiles(OrderRootType.CLASSES) to library.getFiles(OrderRootType.SOURCES)
            }

        topClassesPackageNames = classes.calculateTopPackageNames()
        classesEntriesVirtualFileSystems = classes.calculateEntriesVirtualFileSystems()

        topSourcesPackageNames = sources.calculateTopPackageNames()
        sourcesEntriesVirtualFileSystems = sources.calculateEntriesVirtualFileSystems()
    }

    override val moduleOrigin: ModuleOrigin get() = ModuleOrigin.LIBRARY

    override val name: Name = Name.special("<library ${library.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("library.0", library.presentableName)

    override val contentScope: GlobalSearchScope
        get() = LibraryWithoutSourceScope(project, topClassesPackageNames, classesEntriesVirtualFileSystems, library)

    override fun dependencies(): List<IdeaModuleInfo> {
        val dependencies = LibraryDependenciesCache.getInstance(project).getLibraryDependencies(this)
        return buildList {
            add(this@LibraryInfo)
            addAll(dependencies.sdk)
            addAll(dependencies.librariesWithoutSelf)
        }
    }

    override fun dependenciesWithoutSelf(): Sequence<IdeaModuleInfo> {
        val dependencies = LibraryDependenciesCache.getInstance(project).getLibraryDependencies(this)
        return dependencies.sdk.asSequence() + dependencies.librariesWithoutSelf.asSequence()
    }

    abstract override val platform: TargetPlatform // must override

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.findAnalyzerServices(project)

    private val _sourcesModuleInfo: SourceForBinaryModuleInfo by lazy {
        LibrarySourceInfo(project, library, this, topSourcesPackageNames, sourcesEntriesVirtualFileSystems)
    }

    override val sourcesModuleInfo: SourceForBinaryModuleInfo
        get() = _sourcesModuleInfo

    override fun getLibraryRoots(): Collection<String> = library.getFiles(OrderRootType.CLASSES).mapNotNull(PathUtil::getLocalPath)

    override fun createModificationTracker(): ModificationTracker =
        if (!project.useLibraryToSourceAnalysis) {
            ModificationTracker.NEVER_CHANGED
        } else {
            ResolutionAnchorAwareLibraryModificationTracker(this)
        }

    val isDisposed get() = library.isDisposed

    override fun checkValidity() {
        if (isDisposed) {
            throw AlreadyDisposedException("Library '${name}' is already disposed")
        }
    }

    override fun toString() = "${this::class.simpleName}@${Integer.toHexString(System.identityHashCode(this))}($library)"
}

private class ResolutionAnchorAwareLibraryModificationTracker(libraryInfo: LibraryInfo) : ModificationTracker {
    private val dependencyModules: List<Module> = if (!libraryInfo.isDisposed) {
        ResolutionAnchorCacheService.getInstance(libraryInfo.project)
            .getDependencyResolutionAnchors(libraryInfo)
            .map { it.module }
    } else {
        emptyList()
    }

    override fun getModificationCount(): Long {
        if (dependencyModules.isEmpty()) {
            return ModificationTracker.NEVER_CHANGED.modificationCount
        }

        val project = dependencyModules.first().project
        val modificationTrackerProvider = KotlinModificationTrackerProvider.getInstance(project)

        return dependencyModules
            .maxOfOrNull(modificationTrackerProvider::getModuleSelfModificationCount)
            ?: ModificationTracker.NEVER_CHANGED.modificationCount
    }
}

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
private class LibraryWithoutSourceScope(
    project: Project,
    topPackageNames: Set<String>?,
    entriesVirtualFileSystems: Set<NewVirtualFileSystem>?,
    private val library: Library
) : PoweredLibraryScopeBase(
    project,
    library.getFiles(OrderRootType.CLASSES),
    VirtualFile.EMPTY_ARRAY,
    topPackageNames,
    entriesVirtualFileSystems
), CombinableSourceAndClassRootsScope {

    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getClassRootForFile(file)

    /**
     * [LibraryWithoutSourceScope] exposes its roots so that they can be integrated into a [CombinedSourceAndClassRootsScope].
     */
    override val roots: Object2IntMap<VirtualFile> get() = entries

    override val modules: Set<Module> get() = emptySet()

    override val includesLibraryRoots: Boolean get() = true

    override fun equals(other: Any?): Boolean = other is LibraryWithoutSourceScope && library == other.library
    override fun calcHashCode(): Int = library.hashCode()
    override fun toString(): String = "LibraryWithoutSourceScope($library)"
}