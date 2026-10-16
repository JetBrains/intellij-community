// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("MouseInfoUtil")

package com.intellij.util.ui

import com.intellij.openapi.diagnostic.fileLogger
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Container
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo

private val LOG = fileLogger()

/**
 * Returns the current pointer info, like [MouseInfo.getPointerInfo], but does not fail while the screen devices change.
 *
 * The JDK can fail with [ArrayIndexOutOfBoundsException] while the screen devices change.
 * See [JBR-5402](https://youtrack.jetbrains.com/issue/JBR-5402).
 *
 * @return the pointer info, or `null` when AWT does not know it or when the screen devices change during the call
 */
fun getPointerInfoOrNull(): PointerInfo? = nullWhileScreenDevicesChange { MouseInfo.getPointerInfo() }

/**
 * Returns the pointer position in the coordinate space of this component, like [Component.getMousePosition],
 * but does not fail while the screen devices change.
 *
 * [Component.getMousePosition] calls [MouseInfo.getPointerInfo], so it fails in the same way as [getPointerInfoOrNull] describes.
 *
 * @return the pointer position, or `null` when the pointer is not over this component, when AWT does not know it,
 * or when the screen devices change during the call
 */
fun Component.getMousePositionOrNull(): Point? = nullWhileScreenDevicesChange { mousePosition }

/**
 * Returns the pointer position in the coordinate space of this container, like [Container.getMousePosition],
 * but does not fail while the screen devices change.
 *
 * [Container.getMousePosition] calls [MouseInfo.getPointerInfo], so it fails in the same way as [getPointerInfoOrNull] describes.
 *
 * @param allowChildren `true` to also return the position when the pointer is over a child of this container
 * @return the pointer position, or `null` when the pointer is not over this container, when AWT does not know it,
 * or when the screen devices change during the call
 */
fun Container.getMousePositionOrNull(allowChildren: Boolean): Point? = nullWhileScreenDevicesChange { getMousePosition(allowChildren) }

private inline fun <T : Any> nullWhileScreenDevicesChange(pointerQuery: () -> T?): T? {
  return try {
    pointerQuery()
  }
  catch (e: ArrayIndexOutOfBoundsException) {
    LOG.debug("Pointer info is unavailable while the screen devices change", e)
    null
  }
}
