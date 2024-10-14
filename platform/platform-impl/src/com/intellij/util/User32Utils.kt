// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.ui.User32Ex
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.trace
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import org.jetbrains.annotations.ApiStatus

private val logger = getLogger<User32Ex>()

@ApiStatus.Internal
fun User32Ex.findWindowsWithText(pid: UInt, windowName: String): List<WinDef.HWND> {
  val result = mutableListOf<WinDef.HWND>()
  findProcessWindow(pid) { hWnd ->
    val lengthNoTerminatingZero = GetWindowTextLength(hWnd)
    if (lengthNoTerminatingZero == 0) { return@findProcessWindow false }

    val lengthWithZero = lengthNoTerminatingZero + 1
    val textArray = CharArray(lengthWithZero)
    val finalTextSize = GetWindowText(hWnd, textArray, lengthWithZero)
    if (finalTextSize == 0) {
      return@findProcessWindow false
    }

    val name = String(textArray, 0, finalTextSize)
    if (name.contains(windowName))
      result.add(hWnd)
    false
  }
  return result
}

@ApiStatus.Internal
// Implemented according to System.Diagnostics.MainWindowFinder.IsMainWindow implementation from .NET 8
fun User32Ex.findMainWindow(pid: UInt): WinDef.HWND? {
  return findProcessWindow(pid) { hWnd ->
    val winOwner = GetWindow(hWnd, /*GW_OWNER*/4)
    if (winOwner != null) {
      logger.trace { "There's owner ($winOwner) of current window ($hWnd). Continue enumeration" }
      return@findProcessWindow false
    }

    if (!IsWindowVisible(hWnd)) {
      logger.trace { "Window is not visible. Continue enumeration" }
      return@findProcessWindow false
    }
    return@findProcessWindow true
  }
}

private const val STOP_ENUMERATION = false
private const val CONTINUE_ENUMERATION = true

@ApiStatus.Internal
fun User32Ex.findProcessWindow(pid: UInt, filter: ((WinDef.HWND) -> Boolean)): WinDef.HWND? {
  logger.trace { "Start looking for a window of process \"$pid\"" }

  var winHandle: WinDef.HWND? = null
  val pidAsInt = pid.toInt()
  EnumWindows(object : User32Ex.EnumThreadWindowsCallback {
    override fun callback(hWnd: WinDef.HWND?, lParam: IntByReference?): Boolean {
      if (hWnd == null) {
        logger.trace { "Window handle is null. Continue enumeration" }
        return CONTINUE_ENUMERATION
      }

      val processIdReference = IntByReference()
      if (!GetWindowThreadProcessId(hWnd, processIdReference)) {
        logger.error { "kernel32:GetWindowThreadProcessId wasn't successful. Continue enumeration" }
        return CONTINUE_ENUMERATION
      }

      if (processIdReference.value != pidAsInt) {
        logger.trace { "Window : $hWnd, pid : ${processIdReference.value}. Continue enumeration" }
        return CONTINUE_ENUMERATION
      }

      if (!filter(hWnd))
        return CONTINUE_ENUMERATION

      winHandle = hWnd
      return STOP_ENUMERATION
    }
  }, null)

  return winHandle
}