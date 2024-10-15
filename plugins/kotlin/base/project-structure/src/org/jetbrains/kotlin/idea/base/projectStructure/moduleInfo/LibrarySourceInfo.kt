// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryEntity
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.PoweredLibraryScopeBase
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices

data class LibrarySourceInfo(
    override val project: Project,
    val library: Library,
    override val binariesModuleInfo: BinaryModuleInfo,
    private val topPackageNames: Set<String>?,
    private val entriesVirtualFileSystems: Set<NewVirtualFileSystem>?
) :
    IdeaModuleInfo, SourceForBinaryModuleInfo {

    val source: EntitySource? = library.findLibraryEntity(project.workspaceModel.currentSnapshot)?.entitySource

    override val name: Name = Name.special("<sources for library ${library.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("sources.for.library.0", library.presentableName)

    override fun sourceScope(): GlobalSearchScope =
        // kotlin stdlib source.jar is known to pack multiple source-sets in the same jar as `.jar!/commonMain/*`, `.jar!/jvmMain/*` etc
        KotlinSourceFilterScope.librarySources(LibrarySourceScope(project, null, entriesVirtualFileSystems, library), project)

    override fun modulesWhoseInternalsAreVisible(): Collection<ModuleInfo> {
        return LibraryInfoCache.getInstance(project)[library]
    }

    override val platform: TargetPlatform
        get() = binariesModuleInfo.platform

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = binariesModuleInfo.analyzerServices

    override fun toString(): String = "LibrarySourceInfo(libraryName=${library.name})"
}

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide 'calcHashCode()'
private class LibrarySourceScope(
    project: Project,
    topPackageNames: Set<String>?,
    entriesVirtualFileSystems: Set<NewVirtualFileSystem>?,
    private val library: Library,
) : PoweredLibraryScopeBase(
    project,
    VirtualFile.EMPTY_ARRAY,
    library.getFiles(OrderRootType.SOURCES),
    topPackageNames,
    entriesVirtualFileSystems
) {
    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getSourceRootForFile(file)
    override fun equals(other: Any?): Boolean = other is LibrarySourceScope && library == other.library
    override fun calcHashCode(): Int = library.hashCode()
    override fun toString(): String = "LibrarySourceScope($library)"
}