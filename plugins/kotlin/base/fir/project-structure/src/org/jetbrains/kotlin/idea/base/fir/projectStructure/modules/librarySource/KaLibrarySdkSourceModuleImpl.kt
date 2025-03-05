// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibrarySdkModuleImpl

internal class KaLibrarySdkSourceModuleImpl(
    override val binaryLibrary: KaLibrarySdkModuleImpl
) : KaLibrarySourceModuleBase() {
    override val sourceRoots: Array<VirtualFile> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        binaryLibrary.sdk.rootProvider.getFiles(OrderRootType.SOURCES)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaLibrarySdkSourceModuleImpl
                && binaryLibrary == other.binaryLibrary
    }

    override fun hashCode(): Int {
        return binaryLibrary.hashCode()
    }
}