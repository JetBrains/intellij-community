// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import sun.swing.SwingUtilities2
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel

open class DefaultPartTitle(open var prefix: String = " ", open var suffix: String = ""): BaseTitlePart {

  protected open val label = TitleLabel()

  private var shortTextWidth: Int = 0
  private var longTextWidth: Int = 0

  protected var state = TitlePart.State.LONG

  override var longText: String = ""
    set(value) {
      if (value == field) return
      field = value
      shortText = value
    }

  override var shortText: String = ""

  override val component: JComponent
    get() = label

  override fun hide() {
    state = TitlePart.State.HIDE
    label.text = ""
  }

  override val isClipped: Boolean
    get() = !(state == TitlePart.State.LONG || state == TitlePart.State.IGNORED)

  override fun ignore() {
    state = TitlePart.State.IGNORED
    label.text = ""
  }

  override fun showLong() {
    label.text = if (longText.isEmpty()) "" else "$prefix$longText$suffix"
    state = TitlePart.State.LONG
  }

  override fun showShort() {
    label.text = if (shortText.isEmpty()) "" else "$prefix$shortText$suffix"
    state = TitlePart.State.SHORT
  }

  fun currentState(): TitlePart.State = if(label.text.isEmpty()) TitlePart.State.HIDE else state

  override val longWidth: Int get() = longTextWidth

  override val shortWidth: Int get() = shortTextWidth

  override val toolTipPart: String
    get() = if (state == TitlePart.State.IGNORED || longText.isEmpty()) "" else "$prefix$longText$suffix"

  override fun setToolTip(value: String?) {
    label.toolTipText = value
  }

  override fun refresh() {
    val fm = label.getFontMetrics(label.font)
    longTextWidth = if (longText.isEmpty()) 0 else SwingUtilities2.stringWidth(label, fm, "$prefix$longText$suffix")
    shortTextWidth = if (shortText.isEmpty()) 0 else SwingUtilities2.stringWidth(label, fm, "$prefix$shortText$suffix")
  }

  open class TitleLabel : JLabel() {
    override fun getInsets(): Insets {
      return Insets(0, 0, 0, 0)
    }
  }
}