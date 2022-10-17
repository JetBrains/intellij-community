package org.jetbrains.completion.full.line.settings.ui

import com.intellij.ui.IconManager
import javax.swing.Icon

object FullLineIcons {
  val Cloud = icon("/icons/cloud.svg")
  val DesktopWindows = icon("/icons/desktop_windows.svg")
  val DesktopMac = icon("/icons/desktop_mac.svg")

  /**
   * @param path **Must** start with an (annoying) `/` at start
   */
  private fun Any.icon(path: String): Icon = IconManager.getInstance().getIcon(path, this::class.java)
}
