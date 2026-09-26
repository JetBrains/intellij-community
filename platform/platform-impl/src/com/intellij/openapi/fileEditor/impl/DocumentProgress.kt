// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.runIf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

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
