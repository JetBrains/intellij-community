// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.google.common.util.concurrent.AtomicDouble
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.EmptyRawProgressReporter
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

abstract class BaseProgressReporter(parentScope: CoroutineScope) : ProgressReporter {

  protected val cs: CoroutineScope = parentScope.childScope(supervisor = false)

  final override fun close() {
    cs.cancel()
  }

  suspend fun awaitCompletion() {
    cs.coroutineContext.job.join()
  }

  /**
   * (-♾️; 0.0) -> this reporter has indeterminate children
   * 0.0 -> initial
   * (0.0; 1.0] -> this reporter has determinate children
   * (1.0; +♾️) -> this reporter is raw
   */
  private val lastFraction = AtomicDouble(0.0)

  final override fun step(endFraction: Double, text: ProgressText?): ProgressReporter {
    if (!(0.0 < endFraction && endFraction <= 1.0)) {
      LOG.error(IllegalArgumentException("End fraction must be in (0.0; 1.0], got: $endFraction"))
      return createStep(duration = 0.0, text)
    }
    while (true) {
      val lastFractionValue = lastFraction.get()
      when {
        lastFractionValue <= 0.0 -> {
          if (lastFraction.compareAndSet(lastFractionValue, endFraction)) {
            return createStep(duration = endFraction, text)
          }
        }
        lastFractionValue <= 1.0 -> {
          if (endFraction <= lastFractionValue) {
            LOG.error(IllegalStateException(
              "New end fraction $endFraction must be greater than the previous end fraction $lastFractionValue"
            ))
            return createStep(duration = 0.0, text)
          }
          else if (lastFraction.compareAndSet(lastFractionValue, endFraction)) {
            return createStep(duration = endFraction - lastFractionValue, text)
          }
        }
        else -> {
          LOG.error(IllegalStateException("Cannot start a child because this reporter is raw."))
          return EmptyProgressReporter
        }
      }
    }
  }

  final override fun durationStep(duration: Double, text: ProgressText?): ProgressReporter {
    if (duration !in 0.0..1.0) {
      LOG.error(IllegalArgumentException("Duration is expected to be a value in [0.0; 1.0], got $duration"))
      return createStep(duration = 0.0, text)
    }
    if (duration == 0.0) {
      return indeterminateStep(text)
    }
    else {
      return determinateStep(duration, text)
    }
  }

  private fun indeterminateStep(text: ProgressText?): ProgressReporter {
    while (true) {
      val lastFractionValue = lastFraction.get()
      when {
        lastFractionValue <= 0.0 -> {
          // indicate that this reporter has an indeterminate child, so rawReporter() would fail
          if (lastFraction.compareAndSet(lastFractionValue, lastFractionValue - 1.0)) {
            return createStep(duration = 0.0, text)
          }
        }
        lastFractionValue <= 1.0 -> {
          return createStep(duration = 0.0, text)
        }
        else -> {
          LOG.error(IllegalStateException("Cannot start an indeterminate child because this reporter is raw."))
          return EmptyProgressReporter
        }
      }
    }
  }

  private fun determinateStep(duration: Double, text: ProgressText?): ProgressReporter {
    while (true) {
      val lastFractionValue = lastFraction.get()
      when {
        lastFractionValue <= 0.0 -> {
          // was indeterminate => set to [duration]
          if (lastFraction.compareAndSet(lastFractionValue, duration)) {
            return createStep(duration, text)
          }
        }
        lastFractionValue < 1.0 -> {
          val newValue = lastFractionValue + duration
          when {
            newValue <= 1.0 -> {
              // happy path
              if (lastFraction.compareAndSet(lastFractionValue, newValue)) {
                return createStep(duration, text)
              }
            }
            newValue < 1.0 + ACCEPTABLE_FRACTION_OVERFLOW -> {
              // force set last fraction to 1.0; next call with the same duration will produce an error
              if (lastFraction.compareAndSet(lastFractionValue, 1.0)) {
                // use what's left between lastFractionValue and 1.0
                val effectiveDuration = 1.0 - lastFractionValue
                return createStep(duration = effectiveDuration, text)
              }
            }
            else -> {
              LOG.error(IllegalStateException("Total duration $newValue must not exceed 1.0, duration: $duration"))
              return createStep(duration = 0.0, text)
            }
          }
        }
        lastFractionValue == 1.0 -> {
          LOG.error(IllegalStateException("Total duration must not exceed 1.0, duration: $duration"))
          return createStep(duration = 0.0, text)
        }
        else -> {
          LOG.error(IllegalStateException("Cannot start a child because this reporter is raw."))
          return EmptyProgressReporter
        }
      }
    }
  }

  protected abstract fun createStep(duration: Double, text: ProgressText?): ProgressReporter

  final override fun rawReporter(): RawProgressReporter {
    val previousFraction = lastFraction.getAndSet(2.0)
    when {
      previousFraction == 2.0 -> {
        LOG.error(IllegalStateException("This reporter was already marked raw."))
        return EmptyRawProgressReporter
      }
      previousFraction != 0.0 -> {
        LOG.error(IllegalStateException(
          "This reporter already has child steps." +
          "Wrap the call into step(endFraction=...) and call rawReporter() inside the newly started child step."
        ))
        return EmptyRawProgressReporter
      }
      else -> {
        return asRawReporter()
      }
    }
  }

  protected abstract fun asRawReporter(): RawProgressReporter
}
