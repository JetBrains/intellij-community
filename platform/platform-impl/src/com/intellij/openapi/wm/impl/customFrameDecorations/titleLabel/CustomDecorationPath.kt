// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.titleLabel

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CustomDecorationPath(disposable: Disposable) : SelectedEditorFilePath(disposable) {
  val panel = JPanel(MigLayout("novisualpadding, ins 0, fillx", "[min!][]")).apply { isOpaque = false }
  val projectName = JLabel("").apply { font = JBUI.Fonts.label().asBold() }

  override fun getView(): JComponent {
    return panel
  }

  init {
    panel.add(projectName)
    panel.add(super.getView(), "growx, wmin 0")
  }

  override fun changeProject(project: Project, dsp: Disposable) {
    projectName.text = "[${project.name}]"
    super.changeProject(project, dsp)
  }

  fun getListenerBounds(): List<Rectangle> {
    val mouseInsets = 2
    val projectLabelRect = getView().bounds

    return if (isClipped()) {
      emptyList()
    }
    else {
      listOf(
        Rectangle(projectLabelRect.x, projectLabelRect.y, mouseInsets, projectLabelRect.height),
        Rectangle(projectLabelRect.x, projectLabelRect.y, projectLabelRect.width, mouseInsets),
        Rectangle(projectLabelRect.x, projectLabelRect.maxY.toInt() - mouseInsets, projectLabelRect.width, mouseInsets),
        Rectangle(projectLabelRect.maxX.toInt() - mouseInsets, projectLabelRect.y, mouseInsets, projectLabelRect.height)
      )
    }
  }
}