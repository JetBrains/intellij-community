// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.wsl.AbstractWslDistribution
import com.intellij.execution.wsl.getWslPathSafe
import io.ktor.util.toLowerCasePreservingASCIIRules
import java.nio.file.Files
import java.nio.file.Path

class FilePathRelativeToDir(path: String) {
  private val path: String

  init {
    if (path.startsWith("/")) throw IllegalArgumentException("Not a relative path: $path")
    this.path = separatorsToUnix(path.trimEnd('/', '\\'))
  }

  val asUnixPath: String get() = separatorsToUnix(path)

  private fun separatorsToUnix(path: String): String = path.replace('\\', '/')

  private fun separatorsToWindows(path: String): String = path.replace('/', '\\')

  val asWindowsPath: String get() = separatorsToWindows(path)

  override fun toString(): String = path
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FilePathRelativeToDir

    return path.toLowerCasePreservingASCIIRules() == other.path.toLowerCasePreservingASCIIRules()
  }

  override fun hashCode(): Int {
    return path.hashCode()
  }


}
typealias WindowsFilePath = Path
typealias LinuxFilePath = String

internal const val AVG_NUM_FILES = 10_000

internal fun createTmpWinFile(distro: AbstractWslDistribution): Pair<WindowsFilePath, LinuxFilePath> {
  val file = Files.createTempFile("intellij", "tmp")
  return Pair(file, distro.getWslPathSafe(file))
}