package org.jetbrains.completion.full.line.language

import com.intellij.ui.IconManager

class JavaIconSet : IconSet {
  override val regular = IconManager.getInstance().getIcon("/icons/java/regular.svg", this::class.java)
  override val redCode = IconManager.getInstance().getIcon("/icons/java/redCode.svg", this::class.java)
}
