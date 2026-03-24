// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.elf

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile


internal class ElfVirtualFile(
  original: VirtualFile,
  text: CharSequence,
  modificationStamp: Long,
) : LightVirtualFile(original, text, modificationStamp) {

  override fun shouldSkipEventSystem(): Boolean {
    return true
  }
}
