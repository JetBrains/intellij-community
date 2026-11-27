// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.User32Ex
import kotlinx.coroutines.CancellationException
import java.awt.Component
import java.awt.Rectangle
import java.awt.Window

internal fun Window.getNativeNormalBounds(): Rectangle? {
  val (left, top, right, bottom) = getNativeNormalPosition() ?: return null
  return Rectangle(left, top, right - left, bottom - top)
}

/**
 * Returns `WINDOWPLACEMENT.rcNormalPosition` as left, top, right and bottom in the device space.
 * Returns `null` when the OS is not Windows or the window has no native peer.
 */
internal fun Window.getNativeNormalPosition(): IntArray? {
  try {
    val hwnd = getHWnd() ?: return null
    return User32Ex.getWindowNormalPosition(hwnd)
  }
  catch (e: Exception) {
    if (e is ProcessCanceledException || e is CancellationException) throw e
    IDE_FRAME_EVENT_LOG.warn("An error occurred when trying to get the native window bounds from the OS", e)
    return null
  }
}

/**
 * Sets `WINDOWPLACEMENT.rcNormalPosition` of a hidden window to [position] from [getNativeNormalPosition].
 * Returns `false` when the OS is not Windows, the window is visible, or the window has no native peer.
 */
internal fun Window.setNativeNormalPosition(position: IntArray): Boolean {
  try {
    val hwnd = getHWnd() ?: return false
    return User32Ex.setHiddenWindowNormalPosition(hwnd, position)
  }
  catch (e: Exception) {
    if (e is ProcessCanceledException || e is CancellationException) throw e
    IDE_FRAME_EVENT_LOG.warn("An error occurred when trying to set the native window bounds in the OS", e)
    return false
  }
}

private fun Window.getHWnd(): Long? {
  if (!SystemInfoRt.isWindows) return null
  val peerField = Component::class.java.getDeclaredField("peer")
  peerField.isAccessible = true
  val peer = peerField.get(this) ?: return null
  return peer.javaClass.getMethod("getHWnd").invoke(peer) as Long
}
