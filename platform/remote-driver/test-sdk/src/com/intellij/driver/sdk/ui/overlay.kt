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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The background of a step overlay, as `0xRRGGBB`. */
private const val STEP_COLOR: Int = 0x5A5A5A

/** The background of a failure overlay, as `0xRRGGBB`. */
private const val FAILURE_COLOR: Int = 0xC81E1E

/**
 * Shows the path and the time of the running step in an overlay on the IDE of the step, and the failure of a failed step.
 * Register it, or a subclass, as a [StepInterceptor] service. A subclass can turn the overlays off with [isEnabled].
 */
open class StepOverlayInterceptor : StepInterceptor {
  protected open fun isEnabled(): Boolean = true

  override fun <T> intercept(name: String, action: () -> T): T = action()

  override fun <T> intercept(name: String, driver: Driver?, action: () -> T): T {
    if (driver == null || !isEnabled() || overlayUnavailable) return action()
    return driver.startStepOverlay(name).use { overlay ->
      try {
        action()
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        overlay.fail(e)
        throw e
      }
    }
  }
}

/** The time that a failure overlay shows, and the minimum time of an outermost step overlay. */
private val OVERLAY_MIN_DURATION: Duration = 2.seconds

private const val MAX_MESSAGE_LENGTH: Int = 60
private const val MAX_PATH_STEPS: Int = 5

/** About two lines of the overlay. */
private const val MAX_TITLE_LENGTH: Int = 120
private const val MAX_CURRENT_STEP_LENGTH: Int = 60
private const val MAX_OUTER_STEP_LENGTH: Int = 30

/** A nested step shows its overlay only after this time, so a short nested step makes no remote call. */
private val NESTED_STEP_DELAY: Duration = 250.milliseconds
private const val MAX_CAUSES: Int = 10

/** The text of the overlay that the IDE shows. [message] is the part of the message that shows. */
data class ShownOverlay(val title: String, val message: String?)

/** Returns the overlay that the IDE shows now, or an empty list. It runs after the overlay calls that are already sent. */
@TestOnly
fun Driver.shownOverlays(): List<ShownOverlay> = onOverlayThread {
  withContext(OnDispatcher.EDT) {
    val overlay = utility(DriverOverlayRef::class)
    listOfNotNull(overlay.getShownTitle()?.let { ShownOverlay(it, overlay.getShownMessage()) })
  }
}

/**
 * Sends the overlay calls in their order, and schedules the changes of the overlay. It never shuts down.
 * A call does not get the Driver session of the step, so a step in a session of the EDT does not wait for the EDT.
 */
private val overlayThread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
  Thread(runnable, "Driver overlay").apply { isDaemon = true }
}

private fun <T> onOverlayThread(action: () -> T): T {
  try {
    return overlayThread.submit(Callable(action)).get()
  }
  catch (e: ExecutionException) {
    throw e.cause ?: e
  }
}

/**
 * Tells that the overlay call failed in a connected IDE, for example in an IDE without [DriverOverlayRef].
 * Then the overlays stay off until the end of the run. Only one test runs at a time, so one flag is enough.
 */
@Volatile
private var overlayUnavailable: Boolean = false

private fun Driver.isConnectedSafely(): Boolean =
  try {
    isConnected
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    false
  }

private fun Driver.startStepOverlay(name: String): StepOverlay {
  synchronized(OverlayState) {
    val step = StepOverlay(name, this, OverlayState.steps.lastOrNull())
    OverlayState.steps.add(step)
    OverlayState.linger = null
    // A new outermost step starts a new part of the test, for example the next test. The failure of the previous part ends.
    if (step.isOutermost) OverlayState.failure = null
    try {
      OverlayState.refresh()
    }
    catch (e: Throwable) {
      // A control flow exception ends the step at once.
      OverlayState.steps.remove(step)
      throw e
    }
    return step
  }
}

/**
 * The overlay of a running step. The innermost visible step shows its overlay, with the path of the enclosing steps.
 * An outermost step is visible at once. A nested step is visible after [NESTED_STEP_DELAY].
 * When the outermost step ends, its overlay stays until [OVERLAY_MIN_DURATION] from its start ends.
 */
private class StepOverlay(private val name: String, private val driver: Driver, private val parent: StepOverlay?) : AutoCloseable {
  /** The names of the running steps, from the outermost step to this step. */
  private val path: List<String> = parent?.path.orEmpty() + name
  private val title = stepPathTitle(path)
  private val startedAt = TimeSource.Monotonic.markNow()

  /** The IDE adds the time of the step to the title. */
  val request: OverlayRequest = OverlayRequest(driver, title, null, STEP_COLOR, startedAt)

  val isOutermost: Boolean get() = parent == null

  /** The time when the step becomes visible. */
  val visibleAt: TimeMark = if (isOutermost) startedAt else startedAt + NESTED_STEP_DELAY

  /** The failure that this step or a nested step showed. */
  private var shownFailure: Throwable? = null

  /** Shows [failure], unless a nested step already showed it, also in another exception. */
  fun fail(failure: Throwable) {
    synchronized(OverlayState) {
      val shown = shownFailure
      if (shown == null || generateSequence(failure) { it.cause }.take(MAX_CAUSES).none { it === shown }) {
        val text = failureText(failure)?.ellipsize(MAX_MESSAGE_LENGTH)
        val failureRequest = OverlayRequest(driver, "Step '${name.ellipsize(MAX_CURRENT_STEP_LENGTH)}' failed", text, FAILURE_COLOR)
        OverlayState.failure = TimedOverlay(failureRequest, TimeSource.Monotonic.markNow() + OVERLAY_MIN_DURATION)
        OverlayState.refresh()
        shownFailure = failure
      }
      if (shownFailure != null) parent?.shownFailure = shownFailure
    }
  }

  override fun close() {
    synchronized(OverlayState) {
      OverlayState.steps.remove(this)
      // An outermost step is visible from its start, so its overlay stays until the minimum time from the start ends.
      if (isOutermost) OverlayState.linger = TimedOverlay(request, startedAt + OVERLAY_MIN_DURATION)
      OverlayState.refresh()
    }
  }
}

/**
 * Joins the step names with `›`. A deep path keeps the first step and the last steps, and shows `…` between them.
 * A title longer than [MAX_TITLE_LENGTH] hides more enclosing steps. The current step keeps the longest name.
 */
private fun stepPathTitle(path: List<String>): String {
  val current = path.last().ellipsize(MAX_CURRENT_STEP_LENGTH)
  val outer = path.dropLast(1).map { it.ellipsize(MAX_OUTER_STEP_LENGTH) }
  val candidates = sequence {
    yield(buildList {
      if (outer.size < MAX_PATH_STEPS) addAll(outer) else addAll(listOf(outer.first(), "…") + outer.takeLast(MAX_PATH_STEPS - 2))
      add(current)
    })
    if (outer.size >= 2) yield(listOf(outer.first(), "…", current))
    if (outer.isNotEmpty()) yield(listOf("…", current))
  }
  return candidates.map { it.joinToString(" › ") }.firstOrNull { it.length <= MAX_TITLE_LENGTH } ?: current
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

/** One overlay that the IDE of [driver] can show. Two requests with the same text are the same overlay. */
private data class OverlayRequest(
  val driver: Driver,
  val title: String,
  val message: String?,
  val color: Int,
  /** The start of the step, or null for an overlay without a time. */
  val startedAt: TimeMark? = null,
)

private class TimedOverlay(val request: OverlayRequest, val end: TimeMark)

/**
 * The overlay state of all [Driver]s. A test shows its overlays in one IDE, so one state is enough.
 * One state also keeps one step stack when a wrapper of a [Driver] and its delegate both run steps.
 * Use it only with its lock. [refresh] does not call the IDE itself. The overlay thread sends the calls in their order.
 */
private object OverlayState {
  /** The running steps, from the outermost to the innermost. */
  val steps: MutableList<StepOverlay> = ArrayList()

  /** The failure overlay. It has priority over the step overlays. A newer failure replaces it. */
  var failure: TimedOverlay? = null

  /** The overlay of the outermost step after the step ended. */
  var linger: TimedOverlay? = null

  private var shown: OverlayRequest? = null
  private var nextRefresh: ScheduledFuture<*>? = null

  /** Makes the IDE show the overlay that has priority now, and schedules the next change. */
  fun refresh() {
    if (failure?.end?.hasPassedNow() == true) failure = null
    if (linger?.end?.hasPassedNow() == true) linger = null
    val wanted = failure?.request ?: steps.lastOrNull { it.visibleAt.hasPassedNow() }?.request ?: linger?.request
    if (wanted != shown) {
      val old = shown
      shown = wanted
      // A show replaces the previous overlay, so only the end of all overlays needs a hide.
      if (wanted == null) {
        if (old != null) overlayThread.execute { remoteCall(old.driver, "The overlay '${old.title}' was not removed") { hide() } }
      }
      else {
        overlayThread.execute {
          remoteCall(wanted.driver, "The overlay '${wanted.title}' failed") {
            show(wanted.title, wanted.message, wanted.color, wanted.startedAt?.elapsedNow()?.inWholeMilliseconds ?: -1)
          }
        }
      }
    }
    scheduleNextRefresh()
  }

  private fun scheduleNextRefresh() {
    nextRefresh?.cancel(false)
    val nextVisibleStep = steps.lastOrNull()?.visibleAt?.takeUnless { it.hasPassedNow() }
    val end = listOfNotNull(failure?.end, linger?.end, nextVisibleStep).maxByOrNull { it.elapsedNow() } ?: return
    val delayMillis = (-end.elapsedNow()).inWholeMilliseconds.coerceAtLeast(0) + 1
    nextRefresh = overlayThread.schedule({ refreshOnTimer() }, delayMillis, TimeUnit.MILLISECONDS)
  }

  private fun refreshOnTimer() {
    try {
      synchronized(OverlayState) { refresh() }
    }
    catch (e: Throwable) {
      logFailure("The overlay refresh failed", e)
    }
  }
}

/**
 * Calls [DriverOverlayRef] of [driver] on the overlay thread. The IDE paints the overlay later on its EDT.
 * Logs a failure, unless the IDE is gone.
 */
private fun remoteCall(driver: Driver, failureText: String, call: DriverOverlayRef.() -> Unit) {
  try {
    withoutPauseOnIndicators { driver.utility(DriverOverlayRef::class).call() }
  }
  catch (e: Throwable) {
    if (e is InterruptedException) Thread.currentThread().interrupt()
    rethrowControlFlowException(e)
    if (!overlayUnavailable && driver.isConnectedSafely()) {
      overlayUnavailable = true
      logFailure("$failureText. The overlays are off until the end of the run", e)
    }
  }
}

private fun logFailure(text: String, e: Throwable) {
  if (e is InterruptedException) {
    Thread.currentThread().interrupt()
  }
  val causes = generateSequence(e) { it.cause }.take(MAX_CAUSES).joinToString(" <- ") { "${it.javaClass.name}: ${it.message}" }
  fileLogger().warn("$text: $causes")
}

/** Needs an IDE with a Performance Testing plugin that has this class. */
@Remote("com.jetbrains.performancePlugin.remotedriver.DriverOverlay", plugin = REMOTE_ROBOT_MODULE_ID)
private interface DriverOverlayRef {
  fun show(title: String, message: String?, rgb: Int, elapsedMillis: Long)
  fun hide()
  fun getShownTitle(): String?
  fun getShownMessage(): String?
}
