package org.jetbrains.completion.full.line.settings.ui.panels

import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row

interface ComplexRow {
  fun row(builder: LayoutBuilder): Row
}
