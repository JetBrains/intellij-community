// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.wsl.AbstractWslDistribution
import com.intellij.execution.wsl.getWslPath
import java.nio.file.Files
import java.nio.file.Path

typealias FilePathRelativeToDir = String
typealias WindowsFilePath = Path
typealias LinuxFilePath = String

internal const val AVG_NUM_FILES = 10_000

internal fun createTmpWinFile(distro: AbstractWslDistribution): Pair<WindowsFilePath, LinuxFilePath> {
  val file = Files.createTempFile("intellij", "tmp")
  return Pair(file, distro.getWslPath(file))
}