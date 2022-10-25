package org.jetbrains.completion.full.line.language

import com.intellij.ui.IconManager

class TSIconSet : IconSet {
  override val regular = IconManager.getInstance().getIcon("/icons/ts/regular.svg", this::class.java)
  override val redCode = IconManager.getInstance().getIcon("/icons/ts/redCode.svg", this::class.java)
}
