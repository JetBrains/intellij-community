package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

private val suppressed = ThreadLocal.withInitial { false }

/**
 * Runs [action] without the pause on the indicators.
 *
 * The SDK already suppresses the pause for a search of the UI hierarchy, which only reads the IDE.
 * The pause also ends in a modal context, see [waitForIndicatorsBeforeCall].
 * A test needs this function for an action that must run while the IDE is busy.
 * An example is a step that opens a modal dialog while dumb mode is on.
 *
 * A nested call runs [action] and keeps the state of the outer call.
 */
fun <T> withoutPauseOnIndicators(action: () -> T): T {
  if (suppressed.get()) return action()
  suppressed.set(true)
  try {
    return action()
  }
  finally {
    suppressed.set(false)
  }
}

/**
 * Reports that the current thread runs without the pause on the indicators.
 *
 * A [Driver.beforeCall] hook that pauses on the indicators must do nothing when this is true.
 */
@ApiStatus.Internal
fun isPauseOnIndicatorsSuppressed(): Boolean = suppressed.get()

/**
 * Waits until no opened project shows an indicator or is in dumb mode.
 *
 * A [Driver.beforeCall] hook calls this function before each Driver call.
 * The wait ends without a failure when the IDE is in a modal context.
 * A modal dialog can hold an indicator or dumb mode until it closes, and only the next Driver calls can close it.
 * The first call after the dialog closes waits again.
 * The wait for a project also ends when the project is disposed, as when a test opens another project in this window.
 */
@ApiStatus.Internal
fun Driver.waitForIndicatorsBeforeCall(timeout: Duration) {
  // A project that is not opened yet, such as one behind a modal dialog, can stay so until the timeout.
  getOpenProjects().filter { isProjectOpened(it) }.forEach {
    waitForIndicators({ it }, timeout, waitSmartLongEnough = false, stopWaitingInModalContext = true)
  }
}

/**
 * Reports that a modal dialog or a modal progress is open.
 */
fun Driver.isInModalContext(): Boolean {
  return withContext(OnDispatcher.EDT) {
    utility(LaterInvocator::class).isInModalContext()
  }
}

@Remote("com.intellij.openapi.application.impl.LaterInvocator")
internal interface LaterInvocator {
  fun isInModalContext(): Boolean
}
