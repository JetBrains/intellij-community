package org.jetbrains.completion.full.line.language

import com.intellij.ui.IconManager

class JSIconSet : IconSet {
  override val regular = IconManager.getInstance().getIcon("/icons/js/regular.svg", this::class.java)
  override val redCode = IconManager.getInstance().getIcon("/icons/js/redCode.svg", this::class.java)
}
