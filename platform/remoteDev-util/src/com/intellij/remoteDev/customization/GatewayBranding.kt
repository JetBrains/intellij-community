package com.intellij.remoteDev.customization

import com.intellij.icons.AllIcons
import javax.swing.Icon

interface GatewayBranding {
  /**
   * 16x16 icon
   */
  fun getIcon(): Icon
  fun getName(): String
}
class DefaultGatewayBrandingImpl : GatewayBranding {
  override fun getIcon() = AllIcons.Debugger.Console
  override fun getName() = "IDE Backend"
}