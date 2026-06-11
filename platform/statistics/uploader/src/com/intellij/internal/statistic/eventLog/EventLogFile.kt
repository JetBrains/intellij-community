// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.max

data class EventLogFile(val file: File) {
  companion object {
    @ApiStatus.Internal
    @JvmStatic
    fun create(dir: Path, buildType: EventLogBuildType, suffix: String): EventLogFile {
      var file = dir.resolve(newName(buildType, suffix)).toFile()
      while (file.exists()) {
        file = dir.resolve(newName(buildType, suffix)).toFile()
      }
      return EventLogFile(file)
    }

    /**
     * Generates a unique log file name based on the current timestamp, a random UUID, an optional suffix, and a build type.
     * The current timestamp is used for identification purposes but ignored by the sending pipeline.
     * The suffix is only encoded for traceability, it's not parsed or used in any filtering or routing logic during sending.
     * The build type (EAP or RELEASE) is load-bearing; it directly determines which server-side filters apply when sending.
     * If no bucket ranges exist for that build type, all events in the file are rejected.
     */
    private fun newName(buildType: EventLogBuildType, suffix: String): String {
      val rand = UUID.randomUUID().toString()
      val start = rand.indexOf('-')
      val unique = if (start > 0 && start + 1 < rand.length) rand.substring(start + 1) else rand
      val currentTime  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
      return if (suffix.isNotEmpty()) "$currentTime-$unique-$suffix-${buildType.text}.log"
      else "$currentTime-$unique-${buildType.text}.log"
    }
  }

  @ApiStatus.Internal
  fun getType(defaultType: EventLogBuildType = EventLogBuildType.UNKNOWN): EventLogBuildType {
    return when (parseType()) {
      EventLogBuildType.EAP.text -> EventLogBuildType.EAP
      EventLogBuildType.RELEASE.text -> EventLogBuildType.RELEASE
      else -> defaultType
    }
  }

  private fun parseType(): String {
    val name = file.name
    val separator = name.lastIndexOf("-")
    if (separator + 1 < name.length) {
      val startIndex = max(separator + 1, 0)
      val endIndex = name.indexOf(".", startIndex)
      return if (endIndex < 0) name.substring(startIndex) else name.substring(startIndex, endIndex)
    }
    return name
  }
}