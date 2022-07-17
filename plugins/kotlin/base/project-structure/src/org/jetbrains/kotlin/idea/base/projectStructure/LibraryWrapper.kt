// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

// Workaround for duplicated libraries, see KT-42607
@ApiStatus.Internal
class LibraryWrapper(val library: LibraryEx) {
    private val allRootUrls: Set<String> by lazy {
        mutableSetOf<String>().apply {
            for (orderRootType in OrderRootType.getAllTypes()) {
                ProgressManager.checkCanceled()
                addAll(library.rootProvider.getUrls(orderRootType))
            }
        }
    }

    private val excludedRootUrls: Array<String> by lazy {
        library.excludedRootUrls
    }

    private val hashCode by lazy {
        31 + 37 * allRootUrls.hashCode() + excludedRootUrls.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LibraryWrapper) return false

        return allRootUrls == other.allRootUrls && excludedRootUrls.contentEquals(other.excludedRootUrls)
    }

    override fun hashCode(): Int = hashCode

    fun checkValidity() {
        library.checkValidity()
    }
}

fun Library.checkValidity() {
    safeAs<LibraryEx>()?.let {
        if (it.isDisposed) {
            throw AlreadyDisposedException("Library '${name}' is already disposed")
        }
    }
}