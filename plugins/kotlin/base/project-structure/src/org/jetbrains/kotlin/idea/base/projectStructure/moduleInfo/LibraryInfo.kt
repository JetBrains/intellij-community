// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.useLibraryToSourceAnalysis
import org.jetbrains.kotlin.idea.base.projectStructure.scope.PoweredLibraryScopeBase
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices

/**
 * @see [org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache]
 */
abstract class LibraryInfo internal constructor(
    override val project: Project,
    val library: LibraryEx,
) : IdeaModuleInfo, LibraryModuleInfo, BinaryModuleInfo, TrackableModuleInfo {
    override val moduleOrigin: ModuleOrigin get() = ModuleOrigin.LIBRARY

    override val name: Name = Name.special("<library ${library.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("library.0", library.presentableName)

    override val contentScope: GlobalSearchScope
        get() = LibraryWithoutSourceScope(project, library)

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

    private val _sourcesModuleInfo: SourceForBinaryModuleInfo by lazy { LibrarySourceInfo(project, library, this) }

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
    private val library: Library
) : PoweredLibraryScopeBase(project, library.getFiles(OrderRootType.CLASSES), arrayOf()) {

    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getClassRootForFile(file)

    override fun equals(other: Any?) = other is LibraryWithoutSourceScope && library == other.library
    override fun calcHashCode(): Int = library.hashCode()
    override fun toString() = "LibraryWithoutSourceScope($library)"
}