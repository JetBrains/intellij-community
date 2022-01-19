// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.math.max

class EventLogFile(val file: File) {
  companion object {
    @JvmStatic
    fun create(dir: Path, buildType: EventLogBuildType, suffix: String): EventLogFile {
      var file = dir.resolve(newName(buildType, suffix)).toFile()
      while (file.exists()) {
        file = dir.resolve(newName(buildType, suffix)).toFile()
      }
      return EventLogFile(file)
    }

    private fun newName(buildType: EventLogBuildType, suffix: String): String {
      val rand = UUID.randomUUID().toString()
      val start = rand.indexOf('-')
      val unique = if (start > 0 && start + 1 < rand.length) rand.substring(start + 1) else rand
      return if (suffix.isNotEmpty()) "$unique-$suffix-${buildType.text}.log"
      else "$unique-${buildType.text}.log"
    }
  }

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