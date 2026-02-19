// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JPanel

@ApiStatus.Internal
abstract class SegmentedCustomAction : DumbAwareAction(), CustomComponentAction {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createCustomComponent(presentation: Presentation, place: String): SegmentedCustomPanel {
    return SegmentedCustomPanel(presentation)
  }
}

@ApiStatus.Internal
open class SegmentedCustomPanel(protected val presentation: Presentation) : JPanel() {
  private var project: Project? = null

  init {
    presentation.addPropertyChangeListener(
      PropertyChangeListener { evt: PropertyChangeEvent -> presentationChanged(evt) })
  }

  protected fun getProject(): Project? {
    return project ?: run {
      project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))
      project
    }
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