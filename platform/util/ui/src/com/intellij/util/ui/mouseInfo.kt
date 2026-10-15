// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("MouseInfoUtil")

package com.intellij.util.ui

import com.intellij.openapi.diagnostic.fileLogger
import org.jetbrains.annotations.ApiStatus
import java.awt.MouseInfo
import java.awt.PointerInfo

/**
 * Returns the current pointer info, like [MouseInfo.getPointerInfo], but does not fail while the screen devices change.
 *
 * The JDK can fail with [ArrayIndexOutOfBoundsException] while the screen devices change.
 * See [JBR-5402](https://youtrack.jetbrains.com/issue/JBR-5402).
 *
 * @return the pointer info, or `null` when AWT does not know it or when the screen devices change during the call
 */
fun getPointerInfoOrNull(): PointerInfo? {
  return try {
    MouseInfo.getPointerInfo()
  }
  catch (e: ArrayIndexOutOfBoundsException) {
    fileLogger().debug("Pointer info is unavailable while the screen devices change", e)
    null
  }
}
