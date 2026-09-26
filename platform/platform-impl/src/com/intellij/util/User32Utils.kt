// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.ui.User32Ex
import fleet.util.logging.logger
import org.jetbrains.annotations.ApiStatus

private val logger = logger<User32Ex>()

/** @return the top-level windows of the process whose title contains [windowName], as `HWND` values */
@ApiStatus.Internal
fun findWindowsWithText(pid: UInt, windowName: String): List<Long> {
  val result = mutableListOf<Long>()
  findProcessWindow(pid) { hWnd ->
    val name = User32Ex.getWindowText(hWnd)
    if (name.isNotEmpty() && name.contains(windowName)) {
      result.add(hWnd)
    }
    false
  }
  return result
}

/** @return the main window of the process as an `HWND`, or `null`. Follows `System.Diagnostics.MainWindowFinder.IsMainWindow` from .NET 8. */
@ApiStatus.Internal
fun findMainWindow(pid: UInt): Long? {
  return findProcessWindow(pid) { hWnd ->
    val winOwner = User32Ex.getWindow(hWnd, User32Ex.GW_OWNER)
    if (winOwner != 0L) {
      logger.trace { "There's owner ($winOwner) of current window ($hWnd). Continue enumeration" }
      return@findProcessWindow false
    }

    if (!User32Ex.isWindowVisible(hWnd)) {
      logger.trace { "Window is not visible. Continue enumeration" }
      return@findProcessWindow false
    }
    return@findProcessWindow true
  }
}

private const val STOP_ENUMERATION = false
private const val CONTINUE_ENUMERATION = true

/** @return the first top-level window of the process that [filter] accepts, as an `HWND`, or `null` */
@ApiStatus.Internal
fun findProcessWindow(pid: UInt, filter: ((Long) -> Boolean)): Long? {
  logger.trace { "Start looking for a window of process \"$pid\"" }

  var winHandle: Long? = null
  val pidAsInt = pid.toInt()
  User32Ex.enumWindows { hWnd ->
    val processId = User32Ex.getWindowProcessId(hWnd)
    if (processId == 0) {
      logger.error { "user32:GetWindowThreadProcessId wasn't successful. Continue enumeration" }
      return@enumWindows CONTINUE_ENUMERATION
    }

    if (processId != pidAsInt) {
      logger.trace { "Window : $hWnd, pid : $processId. Continue enumeration" }
      return@enumWindows CONTINUE_ENUMERATION
    }

    if (!filter(hWnd)) {
      return@enumWindows CONTINUE_ENUMERATION
    }

    winHandle = hWnd
    STOP_ENUMERATION
  }

  return winHandle
}
