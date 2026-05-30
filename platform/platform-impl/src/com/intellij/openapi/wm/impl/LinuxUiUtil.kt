// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UnixDesktopEnv
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.name

@ApiStatus.Internal
sealed interface ExecResult {
  class Success(val output: String) : ExecResult

  /**
   * [exitValue] cannot be 0
   */
  class ExitValue(val exitValue: Int) : ExecResult
  class Failure : ExecResult
}

@ApiStatus.Internal
object LinuxUiUtil {

  private val LOG = logger<LinuxUiUtil>()

  private val unsupportedCommands = ConcurrentHashMap<String, Boolean>()

  /**
   * Executes the command and returns its output. If the command is unsupported by OS, returns [ExecResult.Failure] and doesn't
   * try to execute/log the problem anymore
   */
  @JvmStatic
  fun exec(errorMessage: String, vararg command: String): ExecResult {
    ThreadingAssertions.assertBackgroundThread()

    if (command.isEmpty()) {
      LOG.error(errorMessage, "No command provided")
      return ExecResult.Failure()
    }

    if (unsupportedCommands.containsKey(command[0])) {
      // Avoid running and logging unsupported commands
      return ExecResult.Failure()
    }

    try {
      val processOutput = ExecUtil.execAndGetOutput(GeneralCommandLine(*command), 5000)
      val exitCode = processOutput.exitCode
      if (exitCode != 0) {
        LOG.debug("$errorMessage: exit code $exitCode")
        return ExecResult.ExitValue(exitCode)
      }

      val output = processOutput.stdout.trim { it <= ' ' }
      return ExecResult.Success(output)
    }
    catch (e: ExecutionException) {
      if (e is ProcessNotCreatedException && isNoFileOrDirectory(e.cause)) {
        unsupportedCommands[command[0]] = true
        LOG.info("$errorMessage: ${e.message}")
        LOG.trace(e)
      }
      else {
        LOG.info(errorMessage, e)
      }
      return ExecResult.Failure()
    }
  }

  /*
   * https://documentation.ubuntu.com/adsys/latest/reference/policies/User%20Policies/Ubuntu/Desktop/Accessibility/screen-reader-enabled/
   */
  @JvmStatic
  fun isGnomeScreenReaderSettingEnabled(): Boolean {
    if (UnixDesktopEnv.CURRENT != UnixDesktopEnv.GNOME) {
      return false
    }

    val commandLine = GeneralCommandLine(
      "gsettings",
      "get",
      "org.gnome.desktop.a11y.applications",
      "screen-reader-enabled",
    )

    val line = ExecUtil.execAndReadLine(commandLine) ?: return false
    return line.trim() == "true"
  }

  @JvmStatic
  fun isOrcaProcessRunning(): Boolean {
    val orcaProcessName = "orca"

    fun baseName(path: String?): String? =
      path?.let { runCatching { Path(it).name }.getOrNull() }

    fun isPythonExecutable(name: String?): Boolean =
      name == "python" || name?.startsWith("python3") == true

    return try {
      ProcessHandle.allProcesses().anyMatch { processHandle ->
        val info = processHandle.info()

        val commandName = baseName(info.command().orElse(null))
        if (commandName == orcaProcessName) {
          return@anyMatch true
        }

        val args = info.arguments().orElse(null)
        return@anyMatch args != null && args.isNotEmpty() &&
                        isPythonExecutable(commandName) &&
                        baseName(args[0]) == orcaProcessName
      }
    }
    catch (e: UnsupportedOperationException) {
      LOG.debug("Failed to make snapshot of processes: ProcessHandle.allProcesses() is not supported", e)
      return false
    }
    catch (e: SecurityException) {
      LOG.debug("Failed to make snapshot of processes: access denied", e)
      return false
    }
  }

  fun isGnomeZoomEnabled(): Boolean {
    if (UnixDesktopEnv.CURRENT != UnixDesktopEnv.GNOME) {
      return false
    }

    return getGSettingsValue("org.gnome.desktop.a11y.applications", "screen-magnifier-enabled") == "true"
  }

  fun isGnomeHighContrastEnabled(): Boolean {
    if (UnixDesktopEnv.CURRENT != UnixDesktopEnv.GNOME) {
      return false
    }

    // GNOME 42+ / Ubuntu 22.04+
    val isHighContrastEnabled = getGSettingsValue("org.gnome.desktop.a11y.interface", "high-contrast")
    if (isHighContrastEnabled != null) {
      return isHighContrastEnabled == "true"
    }

    return isHighContrastTheme(getGSettingsValue("org.gnome.desktop.interface", "gtk-theme"))
           || isHighContrastTheme(getGSettingsValue("org.gnome.desktop.interface", "icon-theme"))
  }

  private fun getGSettingsValue(schema: String, key: String): String? {
    val commandLine = GeneralCommandLine(
      "gsettings",
      "get",
      schema,
      key,
    )

    return ExecUtil.execAndReadLine(commandLine)?.trim()
  }

  private fun isHighContrastTheme(theme: String?): Boolean {
    val normalizedTheme = theme
      ?.trim()
      ?.removeSurrounding("'")
      ?.removeSurrounding("\"")

    return normalizedTheme.equals("HighContrast", ignoreCase = true) ||
           normalizedTheme.equals("HighContrastInverse", ignoreCase = true)
  }
}

/**
 * There is no good API in jdk for such a check. The string comes from the JDK and is not localizable
 * (see os.cpp: `X(ENOENT, "No such file or directory")`), therefore, this solution should work on different OS-s and locales.
 */
private fun isNoFileOrDirectory(e: Throwable?): Boolean {
  return e?.message?.contains("No such file or directory") == true
}

@ApiStatus.Internal
fun ExecResult.output(): String? {
  return (this as? ExecResult.Success)?.output
}
