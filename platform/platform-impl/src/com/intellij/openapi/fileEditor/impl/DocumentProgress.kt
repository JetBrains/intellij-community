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
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.util.progress.ProgressPipe
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.runIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val documentPreparationTracer by lazy {
  TelemetryManager.getTracer(Scope("documentPreparation"))
}

internal class EditorDocumentPreparation(
  coroutineScope: CoroutineScope,
  private val file: VirtualFile,
  loadDocument: suspend () -> Document? = { serviceAsync<FileDocumentManager>().getOrLoadDocumentUnderProgress(file) },
) {
  internal val progress = coroutineScope.createProgressPipe()

  private val document = coroutineScope.async(Dispatchers.Default + ClientId.current.asContextElement(), start = CoroutineStart.LAZY) {
    documentPreparationTracer.spanBuilder("editor document preparation").setAttribute("file", file.name).use {
      progress.collectProgressUpdates { loadDocument() }
    }
  }

  fun start() {
    document.start()
  }

  suspend fun await(): Document? {
    return documentPreparationTracer.spanBuilder("editor prepared document await").setAttribute("file", file.name).use {
      document.await()
    }
  }
}

/** Reports updates from this pipe while [action] runs. */
internal suspend inline fun ProgressPipe.doUnderPreparationProgress(crossinline action: suspend () -> Unit) {
  coroutineScope {
    reportRawProgress { reporter ->
      val updates = launch {
        progressUpdates().collect {
          reporter.text(it.text)
          reporter.details(it.details)
          reporter.fraction(it.fraction)
        }
      }

      try {
        action()
      }
      finally {
        updates.cancel()
      }
    }
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
