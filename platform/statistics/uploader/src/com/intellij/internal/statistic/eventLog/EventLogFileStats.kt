// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException

/**
 * Reads the timestamp of the oldest event from a single event-log file. The timestamp comes from the event itself
 * (each event carries a `time` field set on the client at log time), which is portable across operating systems,
 * unlike file-system creation time.
 */
@ApiStatus.Internal
object EventLogFileStats {
  /**
   * Reads only the first non-blank line of [file] and returns the `time` of that event, i.e. the timestamp of the
   * oldest event in the file. Returns -1 if the file cannot be read or the line cannot be parsed.
   */
  @JvmStatic
  fun readFirstEventMs(file: File): Long {
    try {
      file.bufferedReader().use { reader ->
        var line = reader.readLine()
        while (line != null) {
          if (line.isNotBlank()) return parseEventTime(line)
          line = reader.readLine()
        }
      }
    }
    catch (_: IOException) {
    }
    return -1L
  }

  private fun parseEventTime(line: String): Long {
    return try {
      SerializationHelper.deserializeLogEvent(line).time
    }
    catch (_: Exception) {
      -1L
    }
  }
}
