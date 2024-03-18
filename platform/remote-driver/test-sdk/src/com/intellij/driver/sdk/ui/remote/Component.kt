package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote

@Remote("java.awt.Component")
interface Component {
  val x: Int
  val y: Int
  val width: Int
  val height: Int
  fun isVisible(): Boolean
  fun isEnabled(): Boolean
}