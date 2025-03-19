// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FileUtils")
package org.jetbrains.kotlin.idea.util

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import org.jetbrains.kotlin.idea.KotlinFileType

fun VirtualFile.isKotlinFileType(): Boolean {
    val nameSequence = nameSequence

    if (nameSequence.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION)) return true
    if (nameSequence.endsWith(JavaFileType.DOT_DEFAULT_EXTENSION) ||
        nameSequence.endsWith(JavaClassFileType.DOT_DEFAULT_EXTENSION)) return false

    return FileTypeManager.getInstance().getFileTypeByFileName(nameSequence) == KotlinFileType.INSTANCE
}

fun VirtualFile.isJavaFileType(): Boolean {
    val nameSequence = nameSequence
    return nameSequence.endsWith(JavaFileType.DOT_DEFAULT_EXTENSION) ||
            FileTypeManager.getInstance().getFileTypeByFileName(nameSequence) == JavaFileType.INSTANCE
}

fun getAllFilesRecursively(filesOrDirs: Array<VirtualFile>): Collection<VirtualFile> {
    val result = ArrayList<VirtualFile>()
    for (file in filesOrDirs) {
        VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                result.add(file)
                return true
            }
        })
    }
    return result
}