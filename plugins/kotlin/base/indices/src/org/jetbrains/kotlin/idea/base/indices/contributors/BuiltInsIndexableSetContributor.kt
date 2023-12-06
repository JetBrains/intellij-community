// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.contributors

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInsVirtualFileProvider

internal class BuiltInsIndexableSetContributor: IndexableSetContributor() {
    override fun getAdditionalRootsToIndex(): Set<VirtualFile> {
        return BuiltInsVirtualFileProvider.getInstance().getBuiltInVirtualFiles()
    }
}