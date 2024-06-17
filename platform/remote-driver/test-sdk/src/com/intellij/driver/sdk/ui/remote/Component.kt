package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.BeControlComponentBuilder
import java.awt.Point

@Remote("java.awt.Component")
@BeControlClass(BeControlComponentBuilder::class)
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
  fun getAccessibleContext(): AccessibleContextRef?
}

@Remote("java.awt.Color")
interface ColorRef{
  fun getRGB(): Int
}

@Remote("java.lang.Class")
interface Class

@Remote("javax.accessibility.AccessibleContext")
interface AccessibleContextRef {
  fun getAccessibleName(): String?
}