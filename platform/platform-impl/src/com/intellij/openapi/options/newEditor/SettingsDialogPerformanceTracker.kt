// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.IdeEventQueue
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.UI
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import java.lang.Boolean.getBoolean
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.time.Duration

@ApiStatus.Internal
object SettingsDialogPerformanceTracker {
  const val ENABLED_PROPERTY: String = "perf.test.settings.dialog"

  /**
   * An input event older than this is considered unrelated to the current open, so the measurement starts at the
   * [markOpeningStarted] call instead. Opening Settings programmatically must not be charged for an earlier click.
   */
  private val MAX_TRIGGER_EVENT_AGE_NANOS = TimeUnit.SECONDS.toNanos(1)

  /** Emits product-side OpenTelemetry spans used by IJ-Perf Settings dialog tests when [ENABLED_PROPERTY] is set. */
  private val tracer by lazy { TelemetryManager.getTracer(UI) }

  /** Not expected to change at runtime, so it is read once: the hooks are called in a normal IDE run as well. */
  private val isEnabled = getBoolean(ENABLED_PROPERTY)
  private val activeToken = AtomicReference<MeasurementToken?>(null)

  /**
   * Arms the measurement. The measured interval starts later, at the open site ([markOpeningStarted]),
   * so that harness artifacts (coroutine dispatch, action scheduling) are not included in the span.
   *
   * Only one measurement per IDE session is supported, and the Settings window must not have been opened before it.
   * [SettingsNonModalDialog.getOrCreate] reuses a live window instead of building a new [SettingsEditor], so a second
   * measurement would never observe a page being loaded and would fail on the [awaitPageReady] timeout. A scenario that
   * needs another measurement has to run in its own IDE launch.
   */
  @JvmStatic
  fun start(dialogShownSpanName: String, pageReadySpanName: String, configurableTreeBuiltSpanName: String): MeasurementToken {
    check(isEnabled) { "$ENABLED_PROPERTY must be enabled to measure Settings dialog performance" }
    val token = MeasurementToken(dialogShownSpanName, pageReadySpanName, configurableTreeBuiltSpanName)
    activeToken.set(token)
    return token
  }

  /**
   * Marks the start of the Settings opening, so that a user open and a test open measure the same thing.
   *
   * Must be called at the very beginning of every entry point that opens the Settings window, before the configurable
   * groups are built, because building them is a part of what the user perceives as "opening Settings".
   *
   * When the window is opened by a real input event (a click or a shortcut, as the UI driver does with
   * `driver.robot.use.input.events`), the interval starts at that event
   * ([IdeEventQueue.popupTriggerTime]) rather than at this call, the same primitive the platform already uses
   * for popups. A stale trigger time (a click unrelated to this open) is ignored.
   *
   * Only the first call per measurement wins, so nested entry points cannot shrink the interval.
   */
  @JvmStatic
  fun markOpeningStarted() {
    if (!isEnabled) return
    val token = activeToken.get() ?: return
    token.openingStartedAtNanos.compareAndSet(-1, openingStartTimeNanos())
  }

  private fun openingStartTimeNanos(): Long {
    val now = System.nanoTime()
    val triggerTime = IdeEventQueue.getInstance().popupTriggerTime
    val elapsedSinceTrigger = now - triggerTime
    return if (triggerTime > 0 && elapsedSinceTrigger in 0..MAX_TRIGGER_EVENT_AGE_NANOS) triggerTime else now
  }

  /**
   * Called when the Settings window becomes visible. Only the non-modal dialog is instrumented,
   * because that is the only flavor the performance command opens.
   */
  @JvmStatic
  fun finishDialogShown() {
    if (!isEnabled) return
    val token = activeToken.get() ?: return
    val startedAtNanos = token.openingStartedAtNanos.get()
    // the window was opened through an entry point that does not mark the opening start, so the interval is unknown
    if (startedAtNanos < 0) return
    if (token.dialogShown.compareAndSet(false, true)) {
      tracer.spanBuilder(token.dialogShownSpanName).recordSpan(startedAtNanos, System.nanoTime())
    }
  }

  /**
   * Measures building the configurable tree: instantiating the configurables provided by the extension points and grouping them.
   *
   * The group returned by [com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil.doGetConfigurableGroup] is lazy,
   * so the build must be measured where it actually happens instead of at the call site.
   * Only the first build per measurement is recorded, so a rebuild caused by an extension point change while the dialog is open
   * cannot overwrite the metric.
   */
  @JvmStatic
  fun <T> measureConfigurableTreeBuild(builder: Supplier<T>): T {
    if (!isEnabled) return builder.get()
    val token = activeToken.get() ?: return builder.get()

    val startNanos = System.nanoTime()
    val result = builder.get()
    if (token.configurableTreeBuilt.compareAndSet(false, true)) {
      tracer.spanBuilder(token.configurableTreeBuiltSpanName).recordSpan(startNanos, System.nanoTime())
    }
    return result
  }

  /**
   * Called when a Settings page starts loading. Only the first page of a measurement is measured, because the tree may change
   * the selection more than once while the dialog is being built and a later start would shrink the interval.
   */
  @JvmStatic
  fun startPageLoading() {
    if (!isEnabled) return
    activeToken.get()?.pageLoadingStartedAtNanos?.compareAndSet(-1, System.nanoTime())
  }

  @JvmStatic
  fun finishPageReady() {
    if (!isEnabled) return
    val token = activeToken.get() ?: return
    if (!token.pageReadySettled.compareAndSet(false, true)) return

    val startedAtNanos = token.pageLoadingStartedAtNanos.get()
    if (startedAtNanos < 0) {
      // the page was loaded without going through startPageLoading, so publishing the scenario would hide a missing metric
      token.pageReady.completeExceptionally(IllegalStateException(
        "Settings page became ready without a recorded loading start, so no '${token.pageReadySpanName}' span was recorded"))
      return
    }
    try {
      tracer.spanBuilder(token.pageReadySpanName).recordSpan(startedAtNanos, System.nanoTime())
    }
    finally {
      token.pageReady.complete(Unit)
    }
  }

  suspend fun awaitPageReady(token: MeasurementToken, timeout: Duration) {
    withTimeout(timeout) {
      token.pageReady.await()
    }
  }

  /**
   * Ends the measurement, so that the product events of a later Settings interaction are not attributed to it.
   * Must be called by the code that called [start], and only that code disarms the measurement: a span may still be recorded
   * after the page is ready, and clearing the state earlier would silently drop it.
   */
  @JvmStatic
  fun finish(token: MeasurementToken) {
    activeToken.compareAndSet(token, null)
  }

  class MeasurementToken internal constructor(
    val dialogShownSpanName: String,
    val pageReadySpanName: String,
    val configurableTreeBuiltSpanName: String,
  ) {
    internal val openingStartedAtNanos = AtomicLong(-1)
    internal val dialogShown = AtomicBoolean(false)
    internal val configurableTreeBuilt = AtomicBoolean(false)
    internal val pageReady = CompletableDeferred<Unit>()
    internal val pageReadySettled = AtomicBoolean(false)
    internal val pageLoadingStartedAtNanos = AtomicLong(-1)
  }
}

private fun SpanBuilder.recordSpan(startNanos: Long, endNanos: Long) {
  if (startNanos <= 0 || endNanos < startNanos) return

  val unixNanoDiff = StartUpMeasurer.getStartTimeUnixNanoDiff()
  setStartTimestamp(startNanos + unixNanoDiff, TimeUnit.NANOSECONDS)
    .startSpan()
    .end(endNanos + unixNanoDiff, TimeUnit.NANOSECONDS)
}