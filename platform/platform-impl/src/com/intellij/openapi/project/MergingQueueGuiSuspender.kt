// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Supplier

@ApiStatus.Internal
@VisibleForTesting
class MergingQueueGuiSuspender {
  @Volatile
  private var myCurrentSuspender: ProgressSuspender? = null
  private val myRequestedSuspensions: MutableList<@NlsContexts.ProgressText String> = ContainerUtil.createConcurrentList()

  fun suspendAndRun(activityName: @NlsContexts.ProgressText String, activity: Runnable) {
    heavyActivityStarted(activityName).use { activity.run() }
  }

  suspend fun suspendAndRun(activityName: @NlsContexts.ProgressText String, activity: suspend () -> Unit) {
    heavyActivityStarted(activityName).use { activity() }
  }

  fun heavyActivityStarted(activityName: @NlsContexts.ProgressText String): AccessToken {
    val reason = IdeBundle.message("dumb.service.indexing.paused.due.to", activityName)
    synchronized(myRequestedSuspensions) { myRequestedSuspensions.add(reason) }
    suspendCurrentTask(reason)
    return object : AccessToken() {
      override fun finish() {
        synchronized(myRequestedSuspensions) { myRequestedSuspensions.remove(reason) }
        resumeAutoSuspendedTask(reason)
      }
    }
  }

  fun resumeProgressIfPossible() {
    val suspender = myCurrentSuspender
    if (suspender != null && suspender.isSuspended) {
      suspender.resumeProcess()
    }
  }

  fun <T> setCurrentSuspenderAndSuspendIfRequested(suspender: ProgressSuspender?, runnable: Supplier<T>): T {
    if (suspender == null) return runnable.get()

    LOG.assertTrue(myCurrentSuspender == null, "Already suspended in another thread, or recursive invocation.")
    return try {
      myCurrentSuspender = suspender
      suspendIfRequested(suspender)
      runnable.get()
    }
    finally {
      LOG.assertTrue(myCurrentSuspender == suspender, "Suspender has changed unexpectedly")
      myCurrentSuspender = null
    }
  }

  private fun resumeAutoSuspendedTask(reason: @NlsContexts.ProgressText String) {
    val currentSuspender = myCurrentSuspender
    if (currentSuspender != null && currentSuspender.isSuspended && reason == currentSuspender.suspendedText) {
      currentSuspender.resumeProcess()
      suspendIfRequested(currentSuspender) // take the following reason from the queue (if any)
    }
  }

  private fun suspendIfRequested(suspender: ProgressSuspender) {
    var suspendedReason: String?
    synchronized(myRequestedSuspensions) { suspendedReason = myRequestedSuspensions.lastOrNull() }
    if (suspendedReason != null) {
      suspender.suspendProcess(suspendedReason)
    }
  }

  private fun suspendCurrentTask(reason: @NlsContexts.ProgressText String) {
    val currentSuspender = myCurrentSuspender
    if (currentSuspender != null && !currentSuspender.isSuspended) {
      currentSuspender.suspendProcess(reason)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(MergingQueueGuiSuspender::class.java)
  }
}
