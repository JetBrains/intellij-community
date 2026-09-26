package com.intellij.jcef.frontend

import com.intellij.openapi.application.PluginPathManager
import com.intellij.ui.jcef.JBCefNativeBundleProvider
import kotlin.io.path.absolutePathString

internal class PluggableCefNativeBundleProvider : JBCefNativeBundleProvider {
  override fun isAvailable(): Boolean = true

  override fun getNativeBundlePath(): String? {
    val distDir = PluginPathManager.getPluginDistPath(javaClass, "jcef")
    return distDir?.absolutePathString()
  }
}
