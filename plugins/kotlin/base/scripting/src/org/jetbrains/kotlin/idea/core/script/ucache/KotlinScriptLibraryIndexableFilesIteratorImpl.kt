// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.roots.IndexableFilesIterationMethods
import com.intellij.util.indexing.roots.LibraryIndexableFilesIterator
import com.intellij.util.indexing.roots.kind.LibraryOrigin

class KotlinScriptLibraryIndexableFilesIteratorImpl
private constructor(
    private val libraryName: @NlsSafe String?,
    private val presentableLibraryName: String,
    private val classRoots: List<VirtualFile>,
    private val sourceRoots: List<VirtualFile>
) : LibraryIndexableFilesIterator {

    override fun getDebugName() = "Library ${presentableLibraryName} " +
            "(#${classRoots.validCount()} class roots, " +
            "#${sourceRoots.validCount()} source roots)"

    override fun getIndexingProgressText(): String = IndexingBundle.message(
        "indexable.files.provider.indexing.library.name",
        presentableLibraryName
    )

    override fun getRootsScanningProgressText(): String {
        if (!libraryName.isNullOrEmpty()) {
            return IndexingBundle.message("indexable.files.provider.scanning.library.name", libraryName)
        }
        return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")
    }

    override fun getOrigin(): LibraryOrigin {
        return KotlinScriptLibraryOriginImpl(classRoots, sourceRoots)
    }

    override fun iterateFiles(
        project: Project,
        fileIterator: ContentIterator,
        fileFilter: VirtualFileFilter
    ): Boolean {
        val roots = runReadAction {
            (classRoots.asSequence() + sourceRoots.asSequence()).filter { it.isValid }.toSet()
        }
        return IndexableFilesIterationMethods.iterateRoots(project, roots, fileIterator, fileFilter)
    }

    override fun getRootUrls(project: Project): Set<String> {
        return (classRoots + sourceRoots).map { it.url }.toSet()
    }

    companion object {
        private fun collectFiles(
            library: KotlinScriptLibraryEntity,
            rootType: OrderRootType,
            rootsToFilter: List<VirtualFile>? = null
        ): List<VirtualFile> {
            val reqType = when (rootType) {
                OrderRootType.CLASSES -> KotlinScriptLibraryRootTypeId.COMPILED
                OrderRootType.SOURCES -> KotlinScriptLibraryRootTypeId.SOURCES
                else -> error("unexpected: $rootType")
            }

            val virtualFileManager = VirtualFileManager.getInstance()

            val libraryRoots = library.roots
                .filter { it.type == reqType }
                .mapNotNull { virtualFileManager.findFileByUrl(it.url.url) }

            val rootsToIterate: List<VirtualFile> = rootsToFilter?.filter { root ->
                libraryRoots.find { libraryRoot ->
                    VfsUtil.isAncestor(libraryRoot, root, false)
                } != null
            } ?: libraryRoots.toList()
            return rootsToIterate
        }

        @RequiresReadLock
        @JvmStatic
        fun createIterator(
            library: KotlinScriptLibraryEntity,
            roots: List<VirtualFile>? = null
        ): KotlinScriptLibraryIndexableFilesIteratorImpl? =
            if (library is LibraryEx && library.isDisposed)
                null
            else
                KotlinScriptLibraryIndexableFilesIteratorImpl(
                    library.name, library.name,
                    collectFiles(library, OrderRootType.CLASSES, roots),
                    collectFiles(library, OrderRootType.SOURCES, roots)
                )
    }

    private fun List<VirtualFile>.validCount(): Int = filter { it.isValid }.size
}

internal data class KotlinScriptLibraryOriginImpl(
    override val classRoots: List<VirtualFile>,
    override val sourceRoots: List<VirtualFile>
) : LibraryOrigin