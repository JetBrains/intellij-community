// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface

abstract class KaLibraryEntityBasedLibraryModuleBase : KaEntityBasedLibraryModuleBase<LibraryEntity, LibraryId>() {
    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        computeRoots(LibraryRootTypeId.COMPILED)
    }

    override val libraryName: String get() = entity.name

    @KaPlatformInterface
    override val isSdk: Boolean get() = false

    /**
     * Returns the roots of [rootType]. A jar directory root gives the roots of the archives in the directory.
     * This matches [com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor].
     *
     * The result is deduplicated, as the same archive may be reachable both as a jar directory entry and as a root of its own.
     */
    internal fun computeRoots(rootType: LibraryRootTypeId): List<VirtualFile> {
        return entity.roots
            .filter { it.type == rootType }
            .flatMap { root ->
                val file = root.url.virtualFile ?: return@flatMap emptyList()
                when (root.inclusionOptions) {
                    LibraryRoot.InclusionOptions.ROOT_ITSELF -> listOf(file)
                    LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT -> file.children.orEmpty().mapNotNull { it.toArchiveRoot() }
                    LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY -> collectArchiveRootsRecursively(file)
                }
            }
            .distinct()
    }
}

private fun collectArchiveRootsRecursively(directory: VirtualFile): List<VirtualFile> {
    val result = mutableListOf<VirtualFile>()
    VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Unit>() {
        override fun visitFile(file: VirtualFile): Boolean {
            val archiveRoot = file.toArchiveRoot() ?: return true
            result.add(archiveRoot)
            return false
        }
    })
    return result
}

private fun VirtualFile.toArchiveRoot(): VirtualFile? {
    if (isDirectory || FileTypeRegistry.getInstance().getFileTypeByFileName(nameSequence) !== ArchiveFileType.INSTANCE) return null
    return StandardFileSystems.jar().findFileByPath(path + URLUtil.JAR_SEPARATOR)
}
