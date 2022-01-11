package com.intellij.remoteDev.customization

import com.intellij.icons.AllIcons
import javax.swing.Icon

interface GatewayClientCustomization {
  /**
   * 16x16 icon
   */
  val icon: Icon
  val name: String
}
class DefaultGatewayClientCustomizationImpl : GatewayClientCustomization {
  override val icon get() = AllIcons.Debugger.Console
  override val name get() = "IDE Backend"
}