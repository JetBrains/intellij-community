// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.builtins

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInsVirtualFileProviderBaseImpl
import java.net.URL

internal class IdeBuiltInsVirtualFileProviderImpl : BuiltInsVirtualFileProviderBaseImpl() {
    override fun findVirtualFile(url: URL): VirtualFile? {
        return VfsUtil.findFileByURL(url)
    }
}