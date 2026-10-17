// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * The frames of a stack, kept as data for a later diagnostic, such as the place that created or disposed an object.
 *
 * A [StackTraceElement] holds the names of a class and a method, not the class, so an object that keeps a snapshot keeps no class
 * loader of the code on the stack. The backtrace of a kept [Throwable] holds the class of each frame, so a long-lived holder of a
 * Throwable keeps every plugin on its stack loaded.
 */
@ApiStatus.Internal
class StackTraceSnapshot private constructor(
  /** The frames, or null while [pendingStack] holds the stack. */
  private val resolvedFrames: Array<StackTraceElement>?,
  /** The stack of a hot path capture where the shim converts the whole stack, see [captureOnHotPath]. */
  private val pendingStack: Throwable?,
  private val skippedClassNames: Array<out String>,
  private val limit: Int,
) {
  private val frames: Array<StackTraceElement>
    get() = resolvedFrames ?: Java11Shim.topFrames(pendingStack!!.stackTrace, limit, skippedClassNames)

  /** The first frame: the frame of the code that made the snapshot or that called into the callee, or null for an empty stack. */
  val topFrame: StackTraceElement?
    get() = frames.firstOrNull()

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
    private val OWN_CLASS_NAME: String = StackTraceSnapshot::class.java.name

    /** The top [limit] frames of the stack of the caller, from the frame of the caller down. */
    @JvmStatic
    @JvmOverloads
    fun capture(limit: Int = Int.MAX_VALUE): StackTraceSnapshot = resolved(Java11Shim.INSTANCE.getStackTrace(limit, OWN_CLASS_NAME))

    /** The top [limit] frames of the stack below the frames of [callee]: the stack of the code that called into [callee]. */
    @JvmStatic
    fun captureCallerOf(callee: Class<*>, limit: Int): StackTraceSnapshot = captureCallerOf(limit, callee.name)

    /** The top [limit] frames of the stack below the frames of [calleeClassNames]: the stack of the code that called into them. */
    @JvmStatic
    fun captureCallerOf(limit: Int, vararg calleeClassNames: String): StackTraceSnapshot {
      return resolved(Java11Shim.INSTANCE.getStackTrace(limit, OWN_CLASS_NAME, *calleeClassNames))
    }

    /**
     * The names of [objectClass] and of its superclasses up to [rootClass]. Their constructors and factories run while an object of
     * [objectClass] is created, so a trace of the creation skips their frames.
     */
    @JvmStatic
    fun classChainNames(objectClass: Class<*>, rootClass: Class<*>): Array<String> {
      val names = ArrayList<String>(4)
      var aClass: Class<*>? = objectClass
      while (aClass != null && rootClass.isAssignableFrom(aClass)) {
        names.add(aClass.name)
        aClass = aClass.superclass
      }
      return names.toTypedArray()
    }

    /**
     * The top [limit] frames of the stack below the frames of [calleeClassNames], for a hot path.
     *
     * When the shim cannot read the top of the stack without the whole stack, the snapshot keeps a [Throwable] and converts it only
     * when a diagnostic needs the frames. Only a JVM without the shim of the IDE lacks the fast path, and such a JVM unloads no plugin,
     * so the Throwable keeps no class loader that could unload.
     */
    @JvmStatic
    fun captureOnHotPath(limit: Int, vararg calleeClassNames: String): StackTraceSnapshot {
      return captureOnHotPath(Java11Shim.INSTANCE, limit, calleeClassNames)
    }

    @VisibleForTesting
    internal fun captureOnHotPath(shim: Java11Shim, limit: Int, calleeClassNames: Array<out String>): StackTraceSnapshot {
      val skippedClassNames = arrayOf(OWN_CLASS_NAME, *calleeClassNames)
      if (shim.walksOnlyRequestedFrames) {
        return resolved(shim.getStackTrace(limit, *skippedClassNames))
      }
      return StackTraceSnapshot(resolvedFrames = null, pendingStack = Throwable(), skippedClassNames, limit)
    }

    private fun resolved(frames: Array<StackTraceElement>): StackTraceSnapshot {
      return StackTraceSnapshot(frames, pendingStack = null, skippedClassNames = emptyArray(), limit = frames.size)
    }
  }
}
