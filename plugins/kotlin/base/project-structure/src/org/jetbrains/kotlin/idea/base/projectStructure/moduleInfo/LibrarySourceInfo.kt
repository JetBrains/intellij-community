// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.LibraryModuleSourceInfoBase
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.PoweredLibraryScopeBase
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices

data class LibrarySourceInfo(override val project: Project, val library: Library, override val binariesModuleInfo: BinaryModuleInfo) :
    IdeaModuleInfo, SourceForBinaryModuleInfo, LibraryModuleSourceInfoBase {

    override val name: Name = Name.special("<sources for library ${library.name}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message("sources.for.library.0", library.presentableName)

    override fun sourceScope(): GlobalSearchScope {
        return KotlinSourceFilterScope.librarySources(LibrarySourceScope(project, library), project)
    }

    override fun modulesWhoseInternalsAreVisible(): Collection<ModuleInfo> {
        return LibraryInfoCache.getInstance(project)[library]
    }

    override val platform: TargetPlatform
        get() = binariesModuleInfo.platform

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = binariesModuleInfo.analyzerServices

    override fun toString() = "LibrarySourceInfo(libraryName=${library.name})"
}

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide 'calcHashCode()'
private class LibrarySourceScope(
    project: Project,
    private val library: Library
) : PoweredLibraryScopeBase(project, arrayOf(), library.getFiles(OrderRootType.SOURCES)) {
    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getSourceRootForFile(file)

    override fun equals(other: Any?) = other is LibrarySourceScope && library == other.library
    override fun calcHashCode(): Int = library.hashCode()
    override fun toString() = "LibrarySourceScope($library)"
}