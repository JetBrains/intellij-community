// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.elf

import com.intellij.openapi.application.EditorLockFreeTyping
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.impl.FileDocumentBindingListener
import com.intellij.openapi.vfs.VirtualFile


/**
 * Entry point creating elfDocument and elfVirtualFile
 */
internal class ElfBindingEntryPoint : FileDocumentBindingListener {

  override fun fileDocumentBindingChanged(
    document: Document,
    oldFile: VirtualFile?,
    file: VirtualFile?,
  ) {
    if (EditorLockFreeTyping.isEnabled()) {
      if (oldFile == null && file != null) {
        ElfTheManager.getInstance().bindElfDocument(
          realDocument = document,
          realVirtualFile = file,
        )
      } else {
        // TODO: handle reload
      }
    }
  }
}
