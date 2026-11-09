package com.intellij.driver.sdk.ui

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.StepInterceptor
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.withoutPauseOnIndicators
import com.intellij.openapi.diagnostic.fileLogger
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

/** The background of a step overlay, as `0xRRGGBB`. */
private const val STEP_COLOR: Int = 0x5A5A5A

/** The background of a failure overlay, as `0xRRGGBB`. */
private const val FAILURE_COLOR: Int = 0xC81E1E

/** The time that a failure overlay shows, and the minimum time of an outermost step overlay. */
private val OVERLAY_MIN_DURATION: Duration = 2.seconds

/** A nested step shows its overlay only after this time, so a short nested step makes no remote call. */
private val NESTED_STEP_DELAY: Duration = 250.milliseconds

/** The longest wait for the IDE to apply the overlay calls. */
private val OVERLAY_CALL_TIMEOUT: Duration = 10.seconds

private const val MAX_NAME_LENGTH: Int = 90
private const val MAX_OUTER_NAME_LENGTH: Int = 45
private const val MAX_OUTER_STEPS: Int = 3
private const val MAX_MESSAGE_LENGTH: Int = 90
private const val MAX_CAUSES: Int = 10

/**
 * Shows the path and the time of the running step in an overlay on the IDE of the step, and the failure of a failed step.
 * Register it, or a subclass, as a [StepInterceptor] service. A subclass can turn the overlays off with [isEnabled].
 */
open class StepOverlayInterceptor : StepInterceptor {
  protected open fun isEnabled(): Boolean = true

  override fun <T> intercept(name: String, action: () -> T): T = action()

  override fun <T> intercept(name: String, driver: Driver?, action: () -> T): T {
    if (driver == null || !isEnabled() || driver.overlayUnavailable) return action()
    val step = OverlayState.start(driver, name)
    try {
      return action()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      OverlayState.fail(step, e)
      throw e
    }
    finally {
      OverlayState.end(step)
    }
  }
}

/** The text of the overlay that the IDE shows. */
data class ShownOverlay(val title: String, val message: String?)

/** Returns the overlay that the IDE shows now, after the overlay calls that are already sent. */
@TestOnly
fun Driver.shownOverlay(): ShownOverlay? {
  try {
    return overlayThread.submit<ShownOverlay?> { readShownOverlay() }.get(OVERLAY_CALL_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
  }
  catch (e: ExecutionException) {
    throw e.cause ?: e
  }
}

/**
 * Runs [action] with no overlay on the screen of the IDE, and shows the overlay of the running step again after it.
 * Use it for a read of the screen, for example a pixel colour, because an overlay paints over the IDE.
 */
@TestOnly
fun <T> withoutStepOverlays(action: () -> T): T {
  val hiddenIn = OverlayState.suppress()
  try {
    hiddenIn?.awaitOverlayCalls()
    return action()
  }
  finally {
    OverlayState.release()
  }
}

/** Logs a failure, because a missed removal only makes the screen read worse. */
private fun Driver.awaitOverlayCalls() {
  try {
    overlayThread.submit { readShownOverlay() }.get(OVERLAY_CALL_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
  }
  catch (e: ExecutionException) {
    fileLogger().warn("The wait for the removal of the overlay failed", e)
  }
  catch (e: TimeoutException) {
    fileLogger().warn("The wait for the removal of the overlay failed", e)
  }
}

/** The EDT call also waits until the IDE applied the overlay calls that are already sent. */
private fun Driver.readShownOverlay(): ShownOverlay? = withContext(OnDispatcher.EDT) {
  val overlay = utility(DriverOverlayRef::class)
  overlay.getShownTitle()?.let { ShownOverlay(it, overlay.getShownMessage()) }
}

/**
 * Sends the overlay calls in their order, and schedules the changes of the overlay. It never shuts down.
 * A call does not get the Driver session of the step, so a step in a session of the EDT does not wait for the EDT.
 */
private val overlayThread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
  Thread(runnable, "Driver overlay").apply { isDaemon = true }
}

/**
 * The [Driver] whose overlay call failed in a connected IDE, for example in an IDE without [DriverOverlayRef].
 * That [Driver] shows no overlay until the end of the run. The reference is weak, because the IDE ends before the run.
 */
@Volatile
private var driverWithoutOverlay: WeakReference<Driver>? = null

private val Driver.overlayUnavailable: Boolean get() = driverWithoutOverlay?.get() === this

/** One overlay that the IDE of [driver] can show. [startedAt] is the start of the step, or null for an overlay without a time. */
private data class OverlayRequest(val driver: Driver, val title: String, val message: String?, val color: Int, val startedAt: ValueTimeMark?)

/** A running step. A nested step is visible after [NESTED_STEP_DELAY]. */
private class Step(val name: String, val request: OverlayRequest, val startedAt: ValueTimeMark, val visibleAt: ValueTimeMark)

private class TimedOverlay(val request: OverlayRequest, val end: ValueTimeMark)

/**
 * The overlay state of all [Driver]s. A test shows its overlays in one IDE, so one state is enough.
 * One state also keeps one step stack when a wrapper of a [Driver] and its delegate both run steps.
 * The priority is: a failure, then the innermost visible step, then the outermost step that ended in the last [OVERLAY_MIN_DURATION].
 */
private object OverlayState {
  /** From the outermost to the innermost. */
  private val steps = ArrayList<Step>()
  private var failure: TimedOverlay? = null
  private var linger: TimedOverlay? = null

  /** The newest failure that an overlay showed. An enclosing step does not show it again. */
  private var shownFailure: Throwable? = null

  private var shown: OverlayRequest? = null
  private var nextRefresh: ScheduledFuture<*>? = null

  /** The number of the running [withoutStepOverlays] calls. */
  private var suppressions: Int = 0

  @Synchronized
  fun start(driver: Driver, name: String): Step {
    val startedAt = markNow()
    val isOutermost = steps.isEmpty()
    val request = OverlayRequest(driver, stepPathTitle(steps.map { it.name } + name), null, STEP_COLOR, startedAt)
    val step = Step(name, request, startedAt, if (isOutermost) startedAt else startedAt + NESTED_STEP_DELAY)
    // A new outermost step starts a new part of the test, for example the next test. The failure of the previous part ends.
    if (isOutermost) {
      failure = null
      shownFailure = null
    }
    linger = null
    steps.add(step)
    refresh()
    return step
  }

  @Synchronized
  fun fail(step: Step, e: Throwable) {
    if (generateSequence(e) { it.cause }.take(MAX_CAUSES).any { it === shownFailure }) return
    shownFailure = e
    val title = "Step '${step.name.ellipsize(MAX_NAME_LENGTH)}' failed"
    val request = OverlayRequest(step.request.driver, title, failureText(e)?.ellipsize(MAX_MESSAGE_LENGTH), FAILURE_COLOR, null)
    failure = TimedOverlay(request, markNow() + OVERLAY_MIN_DURATION)
    refresh()
  }

  @Synchronized
  fun end(step: Step) {
    steps.remove(step)
    if (steps.isEmpty()) linger = TimedOverlay(step.request, step.startedAt + OVERLAY_MIN_DURATION)
    refresh()
  }

  /** Returns the [Driver] of the overlay that goes, or null if the IDE shows none. */
  @Synchronized
  fun suppress(): Driver? {
    val hidden = shown?.driver
    suppressions++
    refresh()
    return hidden
  }

  @Synchronized
  fun release() {
    suppressions--
    refresh()
  }

  @Synchronized
  private fun refreshOnTimer() {
    refresh()
  }

  /** Sends the overlay that has priority now to the IDE, and schedules the next change. */
  private fun refresh() {
    if (failure?.end?.hasPassedNow() == true) failure = null
    if (linger?.end?.hasPassedNow() == true) linger = null
    val wanted = if (suppressions > 0) null
    else failure?.request ?: steps.lastOrNull { it.visibleAt.hasPassedNow() }?.request ?: linger?.request
    val old = shown
    if (wanted != old) {
      shown = wanted
      // A show replaces the previous overlay, so only the end of all overlays needs a hide.
      if (wanted != null) {
        sendCall(wanted.driver) { show(wanted.title, wanted.message, wanted.color, wanted.startedAt?.elapsedNow()?.inWholeMilliseconds ?: -1) }
      }
      else if (old != null) {
        sendCall(old.driver) { hide() }
      }
    }

    nextRefresh?.cancel(false)
    val next = listOfNotNull(failure?.end, linger?.end, steps.lastOrNull()?.visibleAt?.takeUnless { it.hasPassedNow() }).minOrNull() ?: return
    nextRefresh = overlayThread.schedule(::refreshOnTimer, (-next.elapsedNow()).inWholeMilliseconds + 1, TimeUnit.MILLISECONDS)
  }
}

/** Calls [DriverOverlayRef] of [driver] on the overlay thread. On a failure in a connected IDE, the overlays of the IDE go off. */
private fun sendCall(driver: Driver, call: DriverOverlayRef.() -> Unit) {
  overlayThread.execute {
    try {
      withoutPauseOnIndicators { driver.utility(DriverOverlayRef::class).call() }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      if (!driver.overlayUnavailable && driver.isConnectedSafely()) {
        driverWithoutOverlay = WeakReference(driver)
        fileLogger().warn("The overlay call failed. The overlays of this IDE are off until the end of the run", e)
      }
    }
  }
}

private fun Driver.isConnectedSafely(): Boolean =
  try {
    isConnected
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    false
  }

/** Joins the step names with `›`. A deep path keeps the first step and the last steps, and shows `…` between them. */
private fun stepPathTitle(path: List<String>): String {
  val outer = path.dropLast(1).map { it.ellipsize(MAX_OUTER_NAME_LENGTH) }
  val shownOuter = if (outer.size <= MAX_OUTER_STEPS) outer else listOf(outer.first(), "…") + outer.takeLast(MAX_OUTER_STEPS - 1)
  return (shownOuter + path.last().ellipsize(MAX_NAME_LENGTH)).joinToString(" › ")
}

/** Cuts the text to [max] characters at the end of a word, and adds `…`. */
private fun String.ellipsize(max: Int): String {
  if (length <= max) return this
  val cut = take(max - 1)
  val wordEnd = cut.lastIndexOf(' ')
  return (if (wordEnd > max / 2) cut.take(wordEnd) else cut).trimEnd() + "…"
}

/** The message of [failure], or of its cause, or null if neither has one. The title already tells that the step failed. */
private fun failureText(failure: Throwable): String? {
  val text = if (failure is WaitForException) failure.errorMessage else failure.message
  return text?.takeIf { it.isNotBlank() } ?: failure.cause?.message?.takeIf { it.isNotBlank() }
}

/** Needs an IDE with a Performance Testing plugin that has this class. */
@Remote("com.jetbrains.performancePlugin.remotedriver.DriverOverlay", plugin = REMOTE_ROBOT_MODULE_ID)
private interface DriverOverlayRef {
  fun show(title: String, message: String?, rgb: Int, elapsedMillis: Long)
  fun hide()
  fun getShownTitle(): String?
  fun getShownMessage(): String?
}
