// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [BridgeTaskSupport] operates as a bridge between [ProgressIndicatorEx] and [PlatformTaskSupport],
 * allowing status from the indicator be passed to [withBackgroundProgress]
 */
@Service(Service.Level.APP)
@Deprecated("Intended only for support of obsolete API, must not be used outside IdeStatusBarImpl")
internal class BridgeTaskSupport(private val coroutineScope: CoroutineScope) {

  fun withBridgeBackgroundProgress(project: Project?, indicator: ProgressIndicatorEx, info: TaskInfo) {
    project ?: run {
      LOG.error("Project is null for task: ${info.title}, progress is not shown")
      return
    }

    val indicatorFinished = CompletableDeferred<Unit>()
    indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun finish(task: TaskInfo) {
        super.finish(task)
        indicatorFinished.complete(Unit)
      }
    })

    // already finished, progress might not send another finished message
    if (indicator.isFinished(info)) return

    coroutineScope.launch {

      val cancellation = if (info.isCancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable()
      @Suppress("DEPRECATION") val taskSuspender = BridgeTaskSuspender(indicator)

      withBackgroundProgress(project, info.title, cancellation, taskSuspender) {
        launch {
          reportRawProgress { reporter ->
            indicator.addStateDelegate(reporter.toBridgeIndicator())
            indicatorFinished.await() // Keeps the reporter active, so the indicator can report new statuses there
          }
        }

        try {
          indicatorFinished.await()
        }
        catch (ex: CancellationException) {
          // User can cancel the job from UI, which will cause the job cancellation,
          // so we need to cancel the original indicator as well
          if (indicator.isRunning) {
            LOG.info("Progress \"${info.title}\" was cancelled from UI, cancelling $indicator")
            indicator.cancel()
          }
          throw ex
        }
        finally {
          // Wait for the indicator to finish under the "NonCancellable" block,
          // so we won't stop showing the progress bar if the job has been canceled, but the indicator is yet to finish.
          withContext(NonCancellable) {
            indicatorFinished.await()
            taskSuspender.stop()
          }
        }
      }
    }
  }

  @Suppress("UsagesOfObsoleteApi")
  private fun RawProgressReporter.toBridgeIndicator(): ProgressIndicatorEx {
    val reporter = this
    return object : AbstractProgressIndicatorExBase() {
      override fun setText(text: String?) {
        super.setText(text)
        reporter.text(text)
      }

      override fun setText2(text: String?) {
        super.setText2(text)
        reporter.details(text)
      }

      override fun setIndeterminate(indeterminate: Boolean) {
        super.setIndeterminate(indeterminate)
        if (indeterminate)
          reporter.fraction(null)
      }

      override fun setFraction(fraction: Double) {
        super.setFraction(fraction)
        // RawProgressReporter logs an error if the value is not in the interval 0.0-1.0,
        // but ProgressIndicator didn't have that check before (although, according to the documentation, it should be in the range),
        // so some indicators report the wrong value and this can cause error spam - IJPL-166399
        reporter.fraction(fraction.coerceIn(0.0, 1.0))
      }
    }
  }

  companion object {
    private val LOG = logger<BridgeTaskSupport>()

    fun getInstance(): BridgeTaskSupport = service()
  }
}