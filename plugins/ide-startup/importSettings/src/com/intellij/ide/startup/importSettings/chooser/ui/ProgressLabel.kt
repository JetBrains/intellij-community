// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

open class ProgressLabel(txt: String) {
  var text: @NlsContexts.Label String = ""
  set(value) {
    @Suppress("HardCodedStringLiteral") // IDEA-338243
    if (field == value) return
    lbl.text = "<html><center>${value}</center></html>"
    field = value
  }

  protected val lbl = object : JLabel() {
    override fun getPreferredSize(): Dimension {
      val preferredSize = super.getPreferredSize()
      return getPref(preferredSize.height)
    }
  }

  open fun getPref(prefH: Int): Dimension {
    return Dimension(0, prefH)
  }

  val label: JComponent
    get() {
      return lbl
    }

  init {
    lbl.isOpaque = false
    lbl.minimumSize = Dimension(10, lbl.minimumSize.height)
    lbl.horizontalAlignment = SwingConstants.CENTER
    lbl.verticalAlignment = SwingConstants.TOP
    text = txt
  }
}

class ProgressCommentLabel(txt: String) : ProgressLabel(txt) {
  init {
    lbl.font = JBFont.medium()
  }
  override fun getPref(prefH: Int): Dimension {
    return Dimension(0, Math.max(prefH, JBUI.scale(45)))
  }
}