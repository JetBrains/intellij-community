// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType

fun VirtualFile.isKotlinFileType(): Boolean =
    extension == KotlinFileType.EXTENSION || FileTypeRegistry.getInstance().isFileOfType(this, KotlinFileType.INSTANCE)

fun VirtualFile.isJavaFileType(): Boolean =
    extension == JavaFileType.DEFAULT_EXTENSION || FileTypeRegistry.getInstance().isFileOfType(this, JavaFileType.INSTANCE)
