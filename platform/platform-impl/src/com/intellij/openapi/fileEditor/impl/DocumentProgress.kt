// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.runIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

internal class EditorDocumentPreparation(
  coroutineScope: CoroutineScope,
  file: VirtualFile,
  loadDocument: suspend () -> Document? = { serviceAsync<FileDocumentManager>().getOrLoadDocumentUnderProgress(file) },
) {
  internal val progress = coroutineScope.createProgressPipe()

  private val document = coroutineScope.async(Dispatchers.Default + ClientId.current.asContextElement(), start = CoroutineStart.LAZY) {
    progress.collectProgressUpdates { loadDocument() }
  }

  fun start() {
    document.start()
  }

  suspend fun await(): Document? {
    return document.await()
  }
}

/**
 * Runs [action] with a progress indicator bound to the current job if no indicator is installed.
 * Use it to surface progress that blocking code reports through the thread progress indicator.
 *
 * Reports to the current progress step. An outer [reportRawProgress] keeps reporting active across cancelable actions
 */
@ApiStatus.Internal
fun <T> withProgressReport(action: () -> T): T {
  if (Cancellation.currentJob() == null || ProgressManager.getInstance().progressIndicator != null) {
    return action()
  }
  return blockingContextToIndicator(action)
}

/**
 * Returns the document of a valid [file], loading it when needed.
 * The load runs under [withProgressReport], so its progress is reported to the current context.
 */
@ApiStatus.Internal
suspend fun FileDocumentManager.getOrLoadDocumentUnderProgress(file: VirtualFile): Document? {
  if (!file.isValid || file.isDirectory) {
    return null
  }
  getCachedDocument(file)?.let {
    return it
  }
  return withContext(Dispatchers.Default) {
    if (FileDocumentManagerBase.isBinaryWithoutDecompiler(file)) {
      return@withContext null
    }
    reportRawProgress {
      readAction {
        runIf(file.isValid) {
          getCachedDocument(file) ?: withProgressReport {
            getDocument(file)
          }
        }
      }
    }
  }
}
