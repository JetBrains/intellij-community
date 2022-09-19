// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Workaround for duplicated libraries, see KT-42607
 * @see [org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache]
 */
@ApiStatus.Internal
class LibraryWrapper internal constructor(val library: LibraryEx) {
    override fun toString(): String {
        return "libraryName=${library.name}${if (!library.isDisposed) ", libraryRoots=${library.getFiles(OrderRootType.CLASSES)}" else " -disposed-"})"
    }

    fun checkValidity() {
        library.checkValidity()
    }
}

fun Library.checkValidity() {
    safeAs<LibraryEx>()?.takeIf { it.isDisposed }?.let {
        throw AlreadyDisposedException("Library '${name}' is already disposed")
    }
}