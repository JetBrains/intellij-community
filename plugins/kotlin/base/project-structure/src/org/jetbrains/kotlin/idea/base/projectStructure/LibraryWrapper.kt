// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.annotations.ApiStatus

// Workaround for duplicated libraries, see KT-42607
@ApiStatus.Internal
class LibraryWrapper(val library: LibraryEx) {
    private val allRootUrls by lazy {
        mutableSetOf<String>().apply {
            for (orderRootType in OrderRootType.getAllTypes()) {
                ProgressManager.checkCanceled()
                addAll(library.rootProvider.getUrls(orderRootType))
            }
        }
    }

    private val hashCode by lazy {
        31 + allRootUrls.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LibraryWrapper) return false

        if (allRootUrls != other.allRootUrls) return false
        return library.excludedRootUrls.contentEquals(other.library.excludedRootUrls)
    }

    override fun hashCode(): Int = hashCode
}