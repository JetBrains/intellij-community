// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils

import org.jetbrains.annotations.ApiStatus

/**
 * Is used to track durations for reporting to FUS
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface StartMoment {
  companion object {
    fun now(): StartMoment {
      return InstantStartMoment()
    }


    private class InstantStartMoment(private val now: Long = System.nanoTime()) : StartMoment {
      override fun getCurrentDuration(): java.time.Duration {
        return java.time.Duration.ofNanos(System.nanoTime() - now)
      }
    }
  }

  fun getCurrentDuration(): java.time.Duration
}

