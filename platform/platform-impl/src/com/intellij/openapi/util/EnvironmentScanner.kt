// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.util.EnvironmentUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

@ApiStatus.Internal
object EnvironmentScanner {
  fun getPathNames(): List<Path> {
    val fs = FileSystems.getDefault()

    @Suppress("IO_FILE_USAGE")
    val pathDelimiter = java.io.File.pathSeparatorChar

    val pathNames = EnvironmentUtil.getEnvironmentMap()["PATH"]?.split(pathDelimiter)
      ?.mapNotNull {
        try {
          fs.getPath(it)
        }
        catch (_: InvalidPathException) {
          null
        }
      }
      ?.filter(Files::exists)
    return pathNames ?: emptyList()
  }

  @OptIn(LowLevelLocalMachineAccess::class)
  fun hasToolInLocalPath(pathNames: List<Path>, executableWithoutExt: String): Boolean {
    val baseNames = if (OS.CURRENT == OS.Windows) {
      sequenceOf(".bat", ".cmd", ".com", ".exe")
        .map { exeSuffix -> executableWithoutExt + exeSuffix }
    }
    else {
      sequenceOf(executableWithoutExt)
    }

    return pathNames.asSequence()
      .flatMap { pathEntry ->
        baseNames.map { basename -> pathEntry.resolve(basename) }
      }
      .filter(Path::isRegularFile)
      .any(Path::isExecutable)
  }
}
