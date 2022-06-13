// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi.fileTypes

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.psi.KotlinBasePsiBundle
import org.jetbrains.kotlin.library.KLIB_METADATA_FILE_EXTENSION

object KlibMetaFileType : FileType {
    override fun getName() = "KNM"

    override fun getDescription(): String {
        @Suppress("DialogTitleCapitalization")
        return KotlinBasePsiBundle.message("klib.metadata.short")
    }

    override fun getDefaultExtension() = KLIB_METADATA_FILE_EXTENSION
    override fun getIcon(): Nothing? = null
    override fun isBinary() = true
    override fun isReadOnly() = true
    override fun getCharset(file: VirtualFile, content: ByteArray): Nothing? = null

    const val STUB_VERSION = 2
}