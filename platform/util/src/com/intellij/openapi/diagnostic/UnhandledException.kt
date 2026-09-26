// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import org.jetbrains.annotations.ApiStatus

/**
 * An exception that hit the top of a thread stack without being caught.
 * If [isInteractive] then it interrupted a user action, for example a modal dialog.
 * If not, it was a background exception. A background exception is also a bug, but it is probably less important.
 * See IJPL-100.
 *
 * This class is a wrapper. It carries [isInteractive] only.
 * A log writer or an error reporter must report the real cause instead.
 * Use [unwrapIfUnhandled] to get it.
 *
 * `processUnhandledException` is the only place that builds this class.
 * Never build one to move the kind between methods. Pass the cause and the kind apart instead.
 * See IJPL-254578.
 */
@ApiStatus.Internal
class UnhandledException(override val cause: Throwable, val isInteractive: Boolean) : Exception(cause) {
  override val message: String? = cause.message

  override fun getLocalizedMessage(): String? = cause.localizedMessage

  /**
   * Report [realCause] to a developer.
   * [unhandledExceptionKind] tells how the exception reached the caller.
   */
  @ConsistentCopyVisibility
  @ApiStatus.Internal
  data class ExceptionAndWrapper internal constructor(val realCause: Throwable, val unhandledExceptionKind: UnhandledExceptionKind)

  companion object {
    /**
     * Returns the real cause of [ex], and the kind that a log writer or an error reporter must use.
     */
    @JvmStatic
    @ApiStatus.Internal
    fun unwrapIfUnhandled(ex: Throwable): ExceptionAndWrapper {
      if (ex !is UnhandledException) {
        return ExceptionAndWrapper(realCause = ex, unhandledExceptionKind = UnhandledExceptionKind.HANDLED)
      }
      val kind = if (ex.isInteractive) UnhandledExceptionKind.INTERACTIVE else UnhandledExceptionKind.BACKGROUND
      return ExceptionAndWrapper(realCause = ex.cause, unhandledExceptionKind = kind)
    }
  }
}
