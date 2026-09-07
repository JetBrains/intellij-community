// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import org.jetbrains.annotations.ApiStatus

/**
 * Tells how an exception reached a log writer or an error reporter.
 * A log writer, an error report and a usage statistic all need this value.
 * See [UnhandledException], IJPL-100 and IJPL-254578.
 */
@ApiStatus.Internal
enum class UnhandledExceptionKind {
  /** An [UnhandledException] wrapped the exception, and the exception interrupted a user action. */
  INTERACTIVE,

  /** An [UnhandledException] wrapped the exception, and the exception hit a background thread. */
  BACKGROUND,

  /** No [UnhandledException] wrapped the exception. Some code caught the exception and reported it. */
  HANDLED,
}
