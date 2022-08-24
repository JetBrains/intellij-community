package org.jetbrains.completion.full.line.language

import com.intellij.ui.IconManager
import icons.PythonIcons

class PythonIconSet : IconSet {
  override val regular = PythonIcons.Python.Python
  override val redCode = IconManager.getInstance().getIcon("/icons/python/redCode.svg", this::class.java)
}
