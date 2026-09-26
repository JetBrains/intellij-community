package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import org.jetbrains.annotations.ApiStatus

private val suppressed = ThreadLocal.withInitial { false }

/**
 * Runs [action] without the pause on the indicators.
 *
 * The SDK already suppresses the pause for a search of the UI hierarchy, which only reads the IDE.
 * A test needs this function for an action that must not wait for the IDE to become idle. A modal
 * dialog is the main case, because it can hold an indicator until it closes.
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
