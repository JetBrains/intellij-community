// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.warmup

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val LOG = fileLogger()
private val fileTypeNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

/**
 * Starts the warm-up for the file of the [document], or for the [fileType] when there is no file.
 */
internal fun CoroutineScope.scheduleHighlighterWarmUp(project: Project, document: Document?, fileType: FileType?) {
  launch(Dispatchers.Default + CoroutineName("editor highlighter preparation")) {
    val file = document?.let { FileDocumentManager.getInstance().getFile(it) }
    val effectiveFileType = file?.fileType ?: fileType ?: return@launch
    if (fileTypeNames.add(effectiveFileType.name)) {
      warmUpHighlighter(project, file, effectiveFileType)
    }
  }
}

/**
 * Creates a highlighter of the [file], or of the [fileType] when the [file] is null, and reads the attributes of a short text.
 * This loads the lexer and highlighter classes and creates the extensions of the file type
 */
@RequiresBackgroundThread
private suspend fun warmUpHighlighter(project: Project, file: VirtualFile?, fileType: FileType) {
  try {
    val factory = serviceAsync<EditorHighlighterFactory>()
    val highlighter = readAction {
      when {
        project.isDisposed -> null
        file != null && file.isValid -> factory.createEditorHighlighter(project, file)
        else -> factory.createEditorHighlighter(project, fileType)
      }
    } ?: return
    highlighter.setText(" ")
    highlighter.createIterator(0).textAttributes
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Cannot prepare the highlighter of ${file?.name ?: fileType.name}", e)
  }
}
