// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.IdleTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

private val LOG = logger<IdleLeakDetectionService>()

private const val RUN_ON_IDLE_KEY = "dev.leak.detection.runOnIdle"
private const val IDLE_DELAY_MINUTES_KEY = "dev.leak.detection.idleDelayMinutes"
private const val DEFAULT_IDLE_DELAY_MINUTES = 30

/** A leaked Project or Editor instance found by the idle run of [ProjectLeakDetector]. */
internal class LeakedInstanceException(message: String) : RuntimeException(message)

/**
 * Runs [ProjectLeakDetector] after `dev.leak.detection.idleDelayMinutes` without user activity, and reports each leak as a [LeakedInstanceException].
 * The exception message has the form of [LeakReporter.buildReport]. Leaks with the same retention path share one exception.
 *
 * The first project open starts the idle watch, once per application.
 * The run does not force a GC. User activity cancels the wait or the run, and the wait starts again.
 * The run occurs only when the `dev.leak.detection.runOnIdle` registry key is on and the automatic error report submission is enabled.
 * The same leak goes to `LOG.error` once per session.
 */
@Service
internal class IdleLeakDetectionService(private val coroutineScope: CoroutineScope) {
  private val started = AtomicBoolean(false)
  private val reportedSignatures = ConcurrentHashMap.newKeySet<String>()

  fun startIdleWatch() {
    if (started.compareAndSet(false, true)) {
      coroutineScope.launch(Dispatchers.IO) {
        IdleTracker.getInstance().events.collectLatest {
          delay(Registry.intValue(IDLE_DELAY_MINUTES_KEY, DEFAULT_IDLE_DELAY_MINUTES).minutes)
          if (isEnabled()) {
            detectAndReport()
          }
        }
      }
    }
  }

  private suspend fun isEnabled(): Boolean {
    return Registry.`is`(RUN_ON_IDLE_KEY, false) && ExceptionAutoReportUtil.isAutoReportEnabled()
  }

  private suspend fun detectAndReport() {
    try {
      LOG.debug("Detecting memory leaks automatically on idle")

      val leaks = ProjectLeakDetector().detect(forceGc = false)
      val reporter = LeakReporter(coroutineScope)
      for (group in selectNotReported(leaks)) {
        LOG.error(LeakedInstanceException(reporter.buildReport(group)))
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("The idle leak detection failed", e)
    }
  }

  /**
   * Groups the leaks by [leakSignature] and returns the groups with a new signature. Records the signatures.
   * One group gets one [LeakedInstanceException].
   */
  @VisibleForTesting
  fun selectNotReported(leaks: List<LeakInfo>): List<List<LeakInfo>> =
    leaks.groupBy(::leakSignature).filterKeys { reportedSignatures.add(it) }.values.toList()
}

internal class IdleLeakDetectionStarter : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (RegistryManager.getInstanceAsync().`is`(RUN_ON_IDLE_KEY, false)) {
      service<IdleLeakDetectionService>().startIdleWatch()
    }
  }
}
