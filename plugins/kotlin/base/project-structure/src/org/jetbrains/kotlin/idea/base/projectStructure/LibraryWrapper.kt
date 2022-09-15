// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.progress.ProgressManager.checkCanceled
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

// Workaround for duplicated libraries, see KT-42607
@ApiStatus.Internal
class LibraryWrapper private constructor(val library: LibraryEx) {
    private val allRootUrlsByType: Map<OrderRootType, Collection<VirtualFile>> by lazy {
        buildMap {
            val rootProvider = library.rootProvider
            for (orderRootType in OrderRootType.getAllTypes()) {
                checkCanceled()
                val urls = rootProvider.getFiles(orderRootType)
                if (urls.isNotEmpty()) {
                    put(orderRootType, urls.toList())
                }
            }
        }
    }

    private val excludedRootUrls: Array<String> by lazy {
        library.excludedRootUrls
    }

    private val hashCode by lazy {
        31 + 37 * allRootUrlsByType.hashCode() + excludedRootUrls.contentHashCode()
    }

    fun getFiles(orderRootType: OrderRootType): Collection<VirtualFile> = allRootUrlsByType[orderRootType] ?: emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LibraryWrapper) return false

        return allRootUrlsByType == other.allRootUrlsByType && excludedRootUrls.contentEquals(other.excludedRootUrls)
    }

    override fun hashCode(): Int = hashCode

    override fun toString(): String {
        return "libraryName=${library.name}${if (!library.isDisposed) ", libraryRoots=${getFiles(OrderRootType.CLASSES)}" else " -disposed-"})"
    }

    fun checkValidity() {
        library.checkValidity()
    }

    companion object {
        fun wrapLibrary(library: Library): LibraryWrapper {
            require(library is LibraryEx) { "Library '${library.presentableName}' does not implement LibraryEx which is not expected" }
            return LibraryWrapper(library)
        }
    }
}

fun Library.checkValidity() {
    safeAs<LibraryEx>()?.takeIf { it.isDisposed }?.let {
        throw AlreadyDisposedException("Library '${name}' is already disposed")
    }
}