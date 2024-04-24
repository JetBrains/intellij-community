package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import java.awt.Point

@Remote("java.awt.Component")
interface Component {
  val x: Int
  val y: Int
  val width: Int
  val height: Int
  fun isVisible(): Boolean
  fun isShowing(): Boolean
  fun isEnabled(): Boolean
  fun isFocusOwner(): Boolean
  fun getLocationOnScreen(): Point
  fun getClass(): Class
  fun getForeground(): ColorRef
  fun getBackground(): ColorRef
}

@Remote("java.awt.Color")
interface ColorRef{
  fun getRGB(): Int
}

@Remote("java.lang.Class")
interface Class