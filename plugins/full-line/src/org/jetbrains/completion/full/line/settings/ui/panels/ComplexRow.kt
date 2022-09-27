package org.jetbrains.completion.full.line.settings.ui.panels

import com.intellij.ui.layout.*

interface ComplexRow {
  fun row(builder: LayoutBuilder): Row
}
