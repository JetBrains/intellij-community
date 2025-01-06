// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

internal class ClassFileInfo private constructor(val path: @NlsSafe String, val bytecode: ByteArray) {
  companion object {
    fun read(file: VirtualFile): ClassFileInfo? {
      return ClassFileInfo(file.path, file.contentsToByteArray(false))
    }

    fun read(file: File): ClassFileInfo? {
      return ClassFileInfo(file.path, FileUtil.loadFileBytes(file))
    }
  }
}