// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.elf

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean


internal class ElfTheManagerImpl(
  private val coroutineScope: CoroutineScope,
) : ElfTheManager {

  private val isCommandInProgress = AtomicBoolean()

  override fun isElfDocument(elfOrRealDocument: Document): Boolean {
    return getRealDocument(elfOrRealDocument) != null
  }

  override fun isRealDocument(elfOrRealDocument: Document): Boolean {
    return getElfDocument(elfOrRealDocument) != null
  }

  override fun getElfDocument(realDocument: Document): DocumentImpl? {
    return realDocument.getUserData(ELF_DOCUMENT_KEY)
  }

  override fun getRealDocument(elfDocument: Document): DocumentImpl? {
    return elfDocument.getUserData(REAL_DOCUMENT_KEY)
  }

  override fun bindElfDocument(realDocument: Document, realVirtualFile: VirtualFile) {
    if (isLockFreeAllowed(realDocument)) {
      runReadActionBlocking {
        bindElfDocumentInternal(realDocument as DocumentImpl, realVirtualFile)
      }
    }
  }

  override fun isElfCommandInProgress(): Boolean {
    return isCommandInProgress.get()
  }

  override fun executeElfCommand(
    commandProject: Project?,
    commandName: @Nls String?,
    commandGroupId: Any?,
    command: Runnable,
  ) {
    val oldVal = isCommandInProgress.get()
    isCommandInProgress.set(true)
    try {
      CommandProcessor.getInstance().executeCommand(
        commandProject,
        command,
        commandName,
        commandGroupId,
      )
    } finally {
      isCommandInProgress.set(oldVal)
    }
  }

  private fun bindElfDocumentInternal(
    realDocument: DocumentImpl,
    realVirtualFile: VirtualFile,
  ) {
    assertNoElfDocument(realDocument)
    val elfDocument = createLockFreeDocument(realDocument)
    startElfDocSync(realDocument, elfDocument)
    val elfVirtualFile = ElfVirtualFile(
      realVirtualFile,
      realDocument.immutableCharSequence,
      realDocument.modificationStamp,
    )
    FileDocumentManagerBase.registerDocument(elfDocument, elfVirtualFile)
  }

  private fun startElfDocSync(
    realDocument: DocumentImpl,
    elfDocument: DocumentImpl,
  ) {
    val sync = ElfDocumentSync(coroutineScope)
    elfDocument.addDocumentListener(sync)
    realDocument.addDocumentListener(sync)
    elfDocument.putUserData(REAL_DOCUMENT_KEY, realDocument)
    realDocument.putUserData(ELF_DOCUMENT_KEY, elfDocument)
  }

  private fun createLockFreeDocument(document: DocumentImpl): DocumentImpl {
    val acceptsSlashR = document.acceptsSlashR()
    val chars = document.immutableCharSequence
    val doc = EditorFactory.getInstance().createDocument(chars, acceptsSlashR, true)
    doc as DocumentImpl
    doc.modificationStamp = document.modificationStamp
    return doc
  }

  private fun assertNoElfDocument(realDocument: Document) {
    val existingElfDocument = getElfDocument(realDocument)
    if (existingElfDocument != null) {
      error("Elf document already bound $existingElfDocument")
    }
  }

  private fun isLockFreeAllowed(document: Document): Boolean {
    return document is DocumentImpl && document.isWriteThreadOnly
  }

  companion object {
    private val ELF_DOCUMENT_KEY: Key<DocumentImpl> = Key.create("ELF_DOCUMENT_KEY")
    private val REAL_DOCUMENT_KEY: Key<DocumentImpl> = Key.create("REAL_DOCUMENT_KEY")
  }
}
