// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkListener

/**
 * Supports multiline (\n) and hyperlink (<hyperlink>...</hyperlink>)
 */
fun Cell.hyperlink(@NlsContexts.Label text: String,
                   style: UIUtil.ComponentStyle? = null,
                   color: Color? = null,
                   bold: Boolean = false,
                   listener: HyperlinkListener? = null): CellBuilder<JComponent> {
  val pane = JPanel()
  pane.layout = BoxLayout(pane, BoxLayout.Y_AXIS)

  val lines = text.split("\n".toRegex())

  for (line in lines) {
    val label = if (line.contains("<hyperlink>"))
      HyperlinkLabel().apply {
        setTextWithHyperlink(line)
        listener?.let { addHyperlinkListener(listener) }
      }
    else
      JLabel(line)

    style?.let { UIUtil.applyStyle(it, label) }
    if (bold) {
      label.font = label.font.deriveFont(Font.BOLD)
    }
    color?.let { label.foreground = color }

    pane.add(label)
  }

  return component(pane)
}
