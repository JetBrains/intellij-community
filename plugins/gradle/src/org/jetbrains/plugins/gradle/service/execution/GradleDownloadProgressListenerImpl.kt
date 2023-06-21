// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.ProgressBuildEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemTaskProgressIndicatorUpdater.getText
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.atomic.AtomicBoolean

internal class GradleDownloadProgressListenerImpl(
  taskId: ExternalSystemTaskId,
  private val project: Project,
  private val cs: CoroutineScope,
  private val mainListener: ExternalSystemTaskNotificationListener
) : ExternalSystemTaskNotificationListenerAdapter(), GradleDownloadProgressListener {

  private val myProgressIndicators: MutableMap<Any, DownloadEventProgressIndicator> = mutableMapOf()
  private val isStopped = AtomicBoolean()

  init {
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(taskId, this)
  }

  override fun updateProgressIndicator(event: ExternalSystemTaskNotificationEvent, runInBackground: Boolean) {
    if (!isStopped.get() && event is ExternalSystemBuildEvent && event.buildEvent is ProgressBuildEvent) {
      val progressIndicator = myProgressIndicators.computeIfAbsent(event.buildEvent.id) {
        when {
          runInBackground -> BackgroundDownloadProgressIndicator(project, cs, event.description)
          else -> ForwardToMainProgressIndicator(mainListener)
        }
      }
      progressIndicator.update(event)
    }
  }

  override fun stopProgressIndicator(event: ExternalSystemTaskNotificationEvent) {
    if (event is ExternalSystemBuildEvent && event.buildEvent is ProgressBuildEvent) {
      myProgressIndicators.remove(event.buildEvent.id)?.stop(event)
    }
  }

  /**
   * We listen to `onEnd` so we cleanup everything even if Gradle crashes.
   */
  override fun onEnd(id: ExternalSystemTaskId) {
    // Stop all indicators that might be still visible, e.g. on Daemon crash
    isStopped.set(true)
    myProgressIndicators.forEach { (_, indicator) -> indicator.forceStop() }
    myProgressIndicators.clear()
  }

  private interface DownloadEventProgressIndicator {
    fun update(event: ExternalSystemBuildEvent)
    fun stop(event: ExternalSystemBuildEvent)
    fun forceStop()
  }

  /**
   * Runs a progress indicator as a separate background progress. We use that when we show a Gradle build progress indicator.
   */
  private class BackgroundDownloadProgressIndicator(private val project: Project, private val cs: CoroutineScope, title: String) : DownloadEventProgressIndicator {

    private val channel: Channel<ProgressBuildEvent> = Channel()

    init {
      startProgressIndicator(title)
    }

    private fun startProgressIndicator(title: String) {
      cs.launch {
        withBackgroundProgress(project, wrapText(title), cancellable = false) {
          withRawProgressReporter {
            var previousFraction = 0.0
            var statusEvent = channel.receiveCatching().getOrNull()
            while (statusEvent != null && statusEvent.fraction < 1.0) {
              val fraction = statusEvent.fraction
              if (fraction > previousFraction) {
                val text = getText(title, statusEvent.progress, statusEvent.total, statusEvent.unit) { wrapText(it) }
                rawProgressReporter?.text(text)
                rawProgressReporter?.fraction(fraction)
                previousFraction = fraction
              }
              statusEvent = channel.receiveCatching().getOrNull()
            }
          }
        }
      }
    }

    override fun update(event: ExternalSystemBuildEvent) {
      cs.launch {
        try {
          channel.send(event.buildEvent as ProgressBuildEvent)
        }
        catch (_: CancellationException) {
        }
      }
    }

    override fun stop(event: ExternalSystemBuildEvent) {
      forceStop()
    }

    override fun forceStop() {
      channel.close()
    }

    private fun wrapText(text: String): @Nls String =
      ExternalSystemBundle.message("progress.update.text", GradleConstants.SYSTEM_ID.readableName, text)

    private val ProgressBuildEvent.fraction
      get(): Double = if (total == 0L) 0.0 else progress.toDouble() / total
  }

  /**
   * Forwards all events to the main progress indicator.
   */
  private class ForwardToMainProgressIndicator(private val mainListener: ExternalSystemTaskNotificationListener) : DownloadEventProgressIndicator {
    override fun update(event: ExternalSystemBuildEvent) {
      mainListener.onStatusChange(event)
    }

    override fun stop(event: ExternalSystemBuildEvent) {
      mainListener.onStatusChange(event)
    }

    override fun forceStop() {
      // Nothing to do here, since we don't own the main progress indicator.
    }
  }
}