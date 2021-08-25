// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import java.awt.Graphics
import java.awt.Graphics2D
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JPanel

abstract class SegmentedCustomAction : DumbAwareAction(), CustomComponentAction {
  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun createCustomComponent(presentation: Presentation, place: String): SegmentedCustomPanel {
    return SegmentedCustomPanel(presentation)
  }
}

open class SegmentedCustomPanel(val presentation: Presentation) : JPanel() {
  init {
    presentation.addPropertyChangeListener(
      PropertyChangeListener { evt: PropertyChangeEvent -> presentationChanged(evt) })


  }

  protected open fun presentationChanged(event: PropertyChangeEvent) {
  }

  override fun paintComponent(g: Graphics) {
    if (ui != null) {
      val scratchGraphics = g.create()
      try {
        if (isOpaque) {
          SegmentedBarPainter.paintDecorations(g.create() as Graphics2D, this, background)
          ui.paint(g, this)
        }
      }
      finally {
        scratchGraphics.dispose()
      }
    }
  }
}