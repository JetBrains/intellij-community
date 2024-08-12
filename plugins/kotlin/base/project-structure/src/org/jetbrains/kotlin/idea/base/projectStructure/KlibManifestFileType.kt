// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.library.KLIB_MANIFEST_FILE_NAME
import javax.swing.Icon

object KlibManifestFileType : FileTypeIdentifiableByVirtualFile {
    private const val KLIB_LINK_DATA_DIR_NAME = "linkdata"

    override fun isMyFileType(file: VirtualFile): Boolean {
        if (file.nameSequence != KLIB_MANIFEST_FILE_NAME) return false
        if (file.parent?.findChild(KLIB_LINK_DATA_DIR_NAME) == null) return false
        return FileTypeRegistry.getInstance().getFileTypeByFileName(file.nameSequence) == FileTypes.UNKNOWN
    }

    override fun getName(): @NonNls String = "KLIB manifest"
    override fun getDescription(): @NlsContexts.Label String = KotlinBaseProjectStructureBundle.message("klib.manifest.description")
    override fun getDefaultExtension(): @NlsSafe String = ""
    override fun getIcon(): Icon = AllIcons.FileTypes.Manifest
    override fun isBinary(): Boolean = false
}
