// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkListener

/**
 * Supports multiline (\n) and hyperlink (<hyperlink>...</hyperlink>)
 * TODO: Should be rewritten with more flexible way
 * 1. Support several hyperlinks
 * 2. Replace components that use <br> tag in text, use \n instead
 * 3. Remove several JLabel instances from implementation
 * 4. Use <a> instead of <hyperlink>
 * 5. Support html links out-of-the-box
 * 6. Rename hyperlink
 * 7. HyperlinkLabel/JLabel have different left indents
 * 7. etc
 *
 * noteRow method should be replaced by this method (or vice versa), other methods/classes with duplicate functionality should be removed
 *
 * @see com.intellij.ui.layout.Cell.comment
 * @see com.intellij.ui.layout.Cell.label
 * @see com.intellij.ui.layout.RowBuilder.noteRow
 * @see com.intellij.ui.layout.RowBuilder.commentRow
 */
@ApiStatus.Experimental
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
