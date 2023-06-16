// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock


/**
 * @see com.intellij.openapi.fileEditor.FileDocumentManager.getFile
 */
@RequiresReadLock
fun Document.findVirtualFile(): VirtualFile? {
  return FileDocumentManager.getInstance().getFile(this)
}

@RequiresReadLock
fun Document.getVirtualFile(): VirtualFile {
  return checkNotNull(findVirtualFile()) {
    "Cannot find virtual file for $this"
  }
}

/**
 * @see com.intellij.psi.PsiDocumentManager.commitDocument
 */
@RequiresWriteLock
fun Document.commitToPsi(project: Project) {
  PsiDocumentManager.getInstance(project).commitDocument(this)
}

/**
 * @see com.intellij.openapi.fileEditor.FileDocumentManager.saveDocument
 */
@RequiresWriteLock
fun Document.saveToDisk() {
  FileDocumentManager.getInstance().saveDocument(this)
}

/**
 * @see com.intellij.openapi.fileEditor.FileDocumentManager.reloadFromDisk
 */
@RequiresWriteLock
fun Document.reloadFromDisk() {
  FileDocumentManager.getInstance().reloadFromDisk(this)
}