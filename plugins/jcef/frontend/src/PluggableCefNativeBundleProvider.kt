package com.intellij.jcef.frontend

import com.intellij.ide.plugins.getPluginDistDirByClass
import com.intellij.ui.jcef.JBCefNativeBundleProvider
import kotlin.io.path.absolutePathString

internal class PluggableCefNativeBundleProvider : JBCefNativeBundleProvider {
  override fun isAvailable(): Boolean = true

  override fun getNativeBundlePath(): String? {
    val distDir = getPluginDistDirByClass(javaClass)?.resolve("jcef")
    return distDir?.absolutePathString()
  }
}
