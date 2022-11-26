package org.jetbrains.completion.full.line.python

import com.intellij.ui.IconManager
import icons.PythonIcons
import org.jetbrains.completion.full.line.language.IconSet

class PythonIconSet : IconSet {
  override val regular = PythonIcons.Python.Python
  override val redCode = IconManager.getInstance().getIcon("/icons/python/redCode.svg", this::class.java)
}
