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
  try {
    if (!SystemInfoRt.isWindows) return null
    val peerField = Component::class.java.getDeclaredField("peer")
    peerField.isAccessible = true
    val peer = peerField.get(this) ?: return null
    val getHWnd = peer.javaClass.getMethod("getHWnd")
    val hwnd = getHWnd.invoke(peer) as Long
    val (left, top, right, bottom) = User32Ex.getWindowNormalPosition(hwnd) ?: return null
    return Rectangle(left, top, right - left, bottom - top)
  }
  catch (e: Exception) {
    if (e is ProcessCanceledException || e is CancellationException) throw e
    IDE_FRAME_EVENT_LOG.warn("An error occurred when trying to get the native window bounds from the OS", e)
    return null
  }
}
