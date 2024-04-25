// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.execution.process.window.to.foreground.logger
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.trace
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference

// Implemented according to System.Diagnostics.MainWindowFinder.IsMainWindow implementation from .NET 8
fun User32Ex.findWindowsWithText(pid: Int, windowName: String): List<WinDef.HWND> {
  val result = mutableListOf<WinDef.HWND>()
  findProcessWindow(pid) { hWnd ->
    val length = GetWindowTextLength(hWnd)
    val textArray = CharArray(length)
    GetWindowText(hWnd, textArray, length + 1)
    val name = String(textArray, 0, textArray.size)
    if (name.contains(windowName))
      result.add(hWnd)
    false
  }
  return result
}

fun User32Ex.findMainWindow(pid: Int): WinDef.HWND? {
  return findProcessWindow(pid) { hWnd ->
    val winOwner = GetWindow(hWnd, /*GW_OWNER*/4)
    if (winOwner != null) {
      logger.trace { "There's owner ($winOwner) of current window ($hWnd). Continue enumeration" }
      return@findProcessWindow true
    }

    if (!IsWindowVisible(hWnd)) {
      logger.trace { "Window is not visible. Continue enumeration" }
      return@findProcessWindow true
    }
    return@findProcessWindow false
  }
}

fun User32Ex.findProcessWindow(pid: Int, filter: ((WinDef.HWND) -> Boolean)): WinDef.HWND? {
  logger.trace { "Start looking for a window of process \"$pid\"" }

  var winHandle: WinDef.HWND? = null
  EnumWindows(object : User32Ex.EnumThreadWindowsCallback {
    override fun callback(hWnd: WinDef.HWND?, lParam: IntByReference?): Boolean {
      if (hWnd == null) {
        logger.trace { "Window handle is null. Continue enumeration" }
        return true
      }

      val processIdReference = IntByReference()
      if (!GetWindowThreadProcessId(hWnd, processIdReference)) {
        logger.error { "kernel32:GetWindowThreadProcessId wasn't successful. Continue enumeration" }
        return true
      }

      if (processIdReference.value != pid) {
        logger.trace { "Window : $hWnd, pid : ${processIdReference.value}. Continue enumeration" }
        return true
      }

      if (filter(hWnd)) {
        winHandle = hWnd
        return false
      }
      return true
    }
  }, null)

  return winHandle
}