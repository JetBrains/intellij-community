package org.jetbrains.completion.full.line.js

import com.intellij.ui.IconManager
import org.jetbrains.completion.full.line.language.IconSet

class JSIconSet : IconSet {
  override val regular = IconManager.getInstance().getIcon("/icons/js/regular.svg", this::class.java)
  override val redCode = IconManager.getInstance().getIcon("/icons/js/redCode.svg", this::class.java)
}
