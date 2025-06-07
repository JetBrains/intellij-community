// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryModuleImpl

internal class KaLibrarySourceModuleImpl(
    override val binaryLibrary: KaLibraryModuleImpl
) : KaLibrarySourceModuleBase() {
    override val sourceRoots: Array<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        binaryLibrary.library.getFiles(OrderRootType.SOURCES)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaLibrarySourceModuleImpl
                && binaryLibrary == other.binaryLibrary
    }

    override fun hashCode(): Int {
        return binaryLibrary.hashCode()
    }
}