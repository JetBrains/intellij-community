// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle

@Internal
interface ClientGraphicsEnvironment {
  companion object {
    @JvmStatic
    fun getInstance(session: ClientAppSession): ClientGraphicsEnvironment = session.service()
  }
  fun getNumScreens(): Int
  fun makeScreenDevice(id: Int): GraphicsDevice
  fun isDisplayLocal(): Boolean
  fun getScreenDevices(): Array<GraphicsDevice>
  fun isInitialized(): Boolean

  fun findGraphicsConfigurationFor(bounds: Rectangle): GraphicsConfiguration {
    val centerX = bounds.x + bounds.width / 2
    val centerY = bounds.y + bounds.height / 2
    return getScreenDevices().map { it.defaultConfiguration }.minBy { it.bounds.distanceTo(centerX, centerY) }
  }

  private fun Rectangle.distanceTo(pointX: Int, pointY: Int): Int {
    var distance = 0

    if (pointX < x) distance += x - pointX
    if (pointY < y) distance += y - pointY
    if (pointX > x + width) distance += pointX - x - width
    if (pointY > y + height) distance += pointY - y - height

    return distance
  }
}