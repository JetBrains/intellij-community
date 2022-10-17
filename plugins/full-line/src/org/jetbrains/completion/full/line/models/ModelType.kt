package org.jetbrains.completion.full.line.models

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.completion.full.line.settings.ui.FullLineIcons
import javax.swing.Icon

enum class ModelType(@NlsSafe val icon: Icon) {
  Cloud(FullLineIcons.Cloud),
  Local(if (SystemInfo.isMac) FullLineIcons.DesktopMac else FullLineIcons.DesktopWindows),
}
