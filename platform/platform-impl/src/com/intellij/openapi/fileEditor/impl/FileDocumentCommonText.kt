// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListenerBackgroundable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

/**
 * The text that the file and the document last had in common. [MemoryDiskConflictResolver] merges against it.
 */
internal class FileDocumentCommonText : FileDocumentManagerListenerBackgroundable {
  override fun fileContentLoaded(file: VirtualFile, document: Document) {
    rememberLastCommonText(file, document)
  }

  override fun fileContentReloaded(file: VirtualFile, document: Document) {
    rememberLastCommonText(file, document)
  }

  override fun afterDocumentSaved(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document)
    if (file != null) {
      rememberLastCommonText(file, document)
    }
  }

  private fun rememberLastCommonText(file: VirtualFile, document: Document) {
    if (!MemoryDiskConflictResolver.isMergeEnabled()) {
      return
    }
    // the text comes first: an edit landing here then fails the stamp check, instead of becoming the common text
    val text = document.immutableCharSequence
    if (document.modificationStamp != file.modificationStamp) {
      return
    }
    // a preview holds only a prefix of the file, which would silently truncate everything past it once merged
    if (FileDocumentManager.getInstance().isPartialPreviewOfALargeFile(document)) {
      return
    }
    // the document text is a persistent rope, so this snapshot shares its structure with subsequent revisions
    // and only the edited parts are retained twice
    document.putUserData(LAST_COMMON_TEXT_KEY, text)
  }

  companion object {
    private val LAST_COMMON_TEXT_KEY: Key<CharSequence> = Key.create("LAST_COMMON_TEXT_KEY")

    @JvmStatic
    fun getLastKnownCommonText(document: Document): CharSequence? {
      return document.getUserData(LAST_COMMON_TEXT_KEY)
    }
  }
}
