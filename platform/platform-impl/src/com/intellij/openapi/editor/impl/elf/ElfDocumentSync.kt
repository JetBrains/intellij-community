// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.elf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.util.DocumentEventUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.TransferredWriteActionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls


internal class ElfDocumentSync(
  private val coroutineScope: CoroutineScope,
) : PrioritizedDocumentListener {

  private val elfToRealSync = ElfToRealSync()
  private val realToElfSync = RealToElfSync()

  override fun getPriority(): Int {
    return Int.MIN_VALUE + 1
  }

  override fun documentChanged(event: DocumentEvent) {
    ThreadingAssertions.assertEventDispatchThread()
    val document = event.document
    val elf = ElfTheManager.getInstance()
    if (elf.isElfDocument(document)) {
      if (!realToElfSync.isInProgress(document.modificationStamp)) {
        elfToRealSync.documentChanged(event)
      }
    } else if (elf.isRealDocument(document)) {
      if (!elfToRealSync.isInProgress(document.modificationStamp)) {
        realToElfSync.documentChanged(event)
      }
    } else {
      error("Unknown document ${document}")
    }
  }

  private fun getElfDocument(document: Document): DocumentImpl {
    val elf = ElfTheManager.getInstance()
    return elf.getElfDocument(document)!!
  }

  private fun getRealDocument(document: Document): DocumentImpl {
    val elf = ElfTheManager.getInstance()
    return elf.getRealDocument(document)!!
  }

  private fun isMoveEvent(event: DocumentEvent): Boolean {
    return DocumentEventUtil.isMoveInsertion(event) ||
           DocumentEventUtil.isMoveDeletion(event)
  }

  private fun assertDocumentAreSynced(sourceDocument: DocumentEvent, targetDocument: DocumentImpl) {
    if (sourceDocument.offset + sourceDocument.oldLength > targetDocument.textLength) {
      error(
        "Text length mismatch: event expects length ${sourceDocument.offset + sourceDocument.oldLength} " +
        "but ELF document has ${targetDocument.textLength}"
      )
    }
  }

  private fun isWriteAccess(): Boolean {
    return ApplicationManager.getApplication().isWriteAccessAllowed()
  }

  private inner class ElfToRealSync : Sync() {
    override fun documentChanged(event: DocumentEvent) {
      val elfDocument = event.document as DocumentImpl
      val realDocument = getRealDocument(elfDocument)
      if (isWriteAccess()) {
        changeDocument(
          sourceEvent = event,
          sourceDocument = elfDocument,
          targetDocument = realDocument,
        )
      } else {
        changeDocumentAsync(
          sourceEvent = event,
          sourceDocument = elfDocument,
          targetDocument = realDocument,
          commandProject = CommandProcessor.getInstance().currentCommandProject,
          commandGroupId = CommandProcessor.getInstance().currentCommandGroupId,
          commandName = CommandProcessor.getInstance().currentCommandName,
        )
      }
    }
  }

  private inner class RealToElfSync : Sync() {
    override fun documentChanged(event: DocumentEvent) {
      val realDocument = event.document as DocumentImpl
      val elfDocument = getElfDocument(realDocument)
      assertDocumentAreSynced(event, elfDocument)
      changeDocument(
        sourceEvent = event,
        sourceDocument = realDocument,
        targetDocument = elfDocument,
      )
    }
  }

  private abstract inner class Sync : DocumentListener {
    protected val inProgress: MutableSet<Long> = mutableSetOf()

    fun isInProgress(modStamp: Long): Boolean {
      return inProgress.contains(modStamp)
    }

    protected fun changeDocument(
      sourceEvent: DocumentEvent,
      sourceDocument: DocumentImpl,
      targetDocument: DocumentImpl,
    ) {
      val sourceModStamp = sourceDocument.modificationStamp
      startModification(sourceModStamp)
      try {
        applyDocumentChange(sourceEvent, targetDocument, sourceModStamp)
      } finally {
        finishModification(sourceModStamp)
      }
    }

    protected fun changeDocumentAsync(
      sourceEvent: DocumentEvent,
      sourceDocument: DocumentImpl,
      targetDocument: DocumentImpl,
      commandProject: Project?,
      commandGroupId: Any?,
      commandName: @Nls String?,
    ) {
      val sourceModStamp = sourceDocument.modificationStamp
      startModification(sourceModStamp)
      val documentChangeCommand = Runnable {
        try {
          applyDocumentChange(sourceEvent, targetDocument, sourceModStamp)
        } finally {
          finishModification(sourceModStamp)
        }
      }
      invokeLaterWithWriteAction {
        ElfTheManager.getInstance().executeElfCommand(
          commandProject,
          commandName,
          commandGroupId,
          documentChangeCommand,
        )
      }
    }

    private fun finishModification(sourceModStamp: Long) {
      inProgress.remove(sourceModStamp)
    }

    private fun startModification(sourceModStamp: Long) {
      val added = inProgress.add(sourceModStamp)
      if (!added) {
        error("Modification stamp $sourceModStamp is already in progress")
      }
    }

    private fun applyDocumentChange(
      sourceEvent: DocumentEvent,
      targetDocument: DocumentImpl,
      sourceModStamp: Long,
    ) {
      val moveOffset = if (isMoveEvent(sourceEvent)) {
        sourceEvent.moveOffset
      } else {
        sourceEvent.offset
      }
      targetDocument.replaceString(
        sourceEvent.offset,
        sourceEvent.offset + sourceEvent.oldLength,
        moveOffset,
        sourceEvent.newFragment,
        sourceModStamp,
        false,
      )
    }

    private fun invokeLaterWithWriteAction(action: () -> Unit) {
      coroutineScope.launch(DISPATCHER) {
        backgroundWriteAction {
          val application = ApplicationManager.getApplication()
          application.service<TransferredWriteActionService>().runOnEdtWithTransferredWriteActionAndWait {
            action()
          }
        }
      }
    }
  }

  companion object {
    private val DISPATCHER by lazy {
      AppExecutorUtil.createBoundedApplicationPoolExecutor("ELF_DOC_DISPATCHER", 1).asCoroutineDispatcher()
    }
  }
}
