// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

private const val COMMAND_LINE_TIMEOUT = 1000
private const val LIBC_LIBRARY_NAME = "LIBC"

@ApiStatus.Internal
object UnixUtil {
  private val LOG = Logger.getInstance(UnixUtil::class.java.name)

  fun getOsInfo(): OsInfo {
    val osRelease = getReleaseData()
    val isUnderWsl = detectIsUnderWsl()
    val glibcVersion = getGlibcVersion()
    return OsInfo(osRelease.distro, osRelease.release, osRelease.prettyName,
                  isUnderWsl, glibcVersion)
  }

  private fun detectIsUnderWsl(): Boolean {
    try {
      @Suppress("SpellCheckingInspection") val kernel = Files.readString(Path.of("/proc/sys/kernel/osrelease"))
      return kernel.contains("-microsoft-")
    }
    catch (_: IOException) {
      return false
    }
  }

  // https://www.freedesktop.org/software/systemd/man/os-release.html
  private fun getReleaseData(): OsRelease {
    try {
      Files.lines(Path.of("/etc/os-release")).use { lines ->
        val fields = setOf("ID", "VERSION_ID", "PRETTY_NAME")
        val values = lines.asSequence()
          .map { it.split('=') }
          .filter { it.size == 2 && it[0] in fields }
          .associate { it[0] to it[1].trim('"') }
        return OsRelease(values["ID"], values["VERSION_ID"], values["PRETTY_NAME"])
      }
    }
    catch (_: IOException) {
      return OsRelease(null, null, null)
    }
  }

  @JvmStatic
  fun getGlibcVersion(): Double? {
    try {
      check(SystemInfo.isLinux) { "glibc version is only supported on Linux" }
      val commandLine = GeneralCommandLine("ldd", "--version")
      val output = ExecUtil.execAndGetOutput(commandLine, COMMAND_LINE_TIMEOUT)
      if (output.exitCode != 0) {
        LOG.debug("Failed to execute ${commandLine.commandLineString} exit code ${output.exitCode}")
        return null
      }
      val outputStr = output.stdout.split("\n").firstOrNull { it.contains(LIBC_LIBRARY_NAME, true) }
      if (outputStr == null) {
        LOG.debug("Failed to find $LIBC_LIBRARY_NAME in ${output.stdout}")
        return null
      }
      val version = outputStr.split(" ").lastOrNull { it.isNotEmpty() } ?: return null
      return version.toDoubleOrNull()
    }
    catch (_: ExecutionException) {
      return null
    }
  }

  data class OsInfo(
    val distro: String?,
    val release: String?,
    val prettyName: String?,
    val isUnderWsl: Boolean,
    val glibcVersion: Double?,
  )

  private data class OsRelease(
    val distro: String?,
    val release: String?,
    val prettyName: String?,
  )
}