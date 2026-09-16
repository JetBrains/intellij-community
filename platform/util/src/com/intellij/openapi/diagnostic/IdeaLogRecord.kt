// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import org.jetbrains.annotations.ApiStatus
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * A log record with the data that [LogRecord] has no field for.
 *
 * A handler on the root logger always gets the real cause in [LogRecord.getThrown].
 * The [UnhandledException] wrapper stops at [JulLogger], so a handler needs no knowledge of it. See IJPL-254578.
 * A handler that needs the kind reads [unhandledExceptionKind] from this class.
 */
@ApiStatus.Internal
class IdeaLogRecord internal constructor(
  level: Level,
  message: String?,
  /**
   * Tells how the exception reached the log writer. See IJPL-100.
   * A plain [LogRecord] means [UnhandledExceptionKind.HANDLED].
   */
  val unhandledExceptionKind: UnhandledExceptionKind,
) : LogRecord(level, message)
