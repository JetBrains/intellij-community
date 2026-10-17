// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.ApiStatus

/**
 * The frames of a stack, kept as data for a later diagnostic, such as the place that created or disposed an object.
 *
 * A [StackTraceElement] holds the names of a class and a method, not the class, so an object that keeps a snapshot keeps no class
 * loader of the code on the stack. The backtrace of a kept [Throwable] holds the class of each frame, so a long-lived holder of a
 * Throwable keeps every plugin on its stack loaded.
 */
@ApiStatus.Internal
class StackTraceSnapshot private constructor(private val frames: Array<StackTraceElement>) {
  /** A new [Throwable] with [message] and the frames of this snapshot, for a log entry or as the cause of an exception. */
  @JvmOverloads
  fun toThrowable(message: String? = null): Throwable {
    val throwable = Throwable(message)
    throwable.stackTrace = frames
    return throwable
  }

  /** The frames, one line each, as a printed stack trace shows them. */
  override fun toString(): String = frames.joinToString(separator = "\n") { "\tat $it" }

  companion object {
    /** The top [limit] frames of the stack of the caller, from the frame of the caller down. */
    @JvmStatic
    @JvmOverloads
    fun capture(limit: Int = Int.MAX_VALUE): StackTraceSnapshot {
      return StackTraceSnapshot(Java11Shim.INSTANCE.getStackTrace(limit, StackTraceSnapshot::class.java.name))
    }

    /** The top [limit] frames of the stack below the frames of [callee]: the stack of the code that called into [callee]. */
    @JvmStatic
    fun captureCallerOf(callee: Class<*>, limit: Int): StackTraceSnapshot {
      return StackTraceSnapshot(Java11Shim.INSTANCE.getStackTrace(limit, StackTraceSnapshot::class.java.name, callee.name))
    }
  }
}
