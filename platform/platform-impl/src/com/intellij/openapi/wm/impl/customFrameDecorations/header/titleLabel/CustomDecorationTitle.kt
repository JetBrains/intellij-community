// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

class CustomDecorationTitle(val frame: JFrame) {
  private var mySelectedEditorFilePath: CustomDecorationPath? = null
  private var active = false

  private val titleChangeListener = PropertyChangeListener{
    titleLabel.text = frame.title
    checkProject()
  }

  private val titleLabel = JLabel()

  var project: Project? = null

  private val pane = object : JPanel(MigLayout("fill, ins 0, gap 0, hidemode 3")) {
    init {
      frame.addPropertyChangeListener("title", titleChangeListener)
      add(titleLabel, "growx")
    }

    override fun addNotify() {
      super.addNotify()
      checkProject()
    }
  }

  private fun checkProject() {
    if(project == null) {
      if(frame is IdeFrame) {
        frame.project?.let {
          project = it

          val title = CustomDecorationPath(frame, it)
          title.setActive(active)

          pane.add(title.getView(), "growx")
          titleLabel.isVisible = false
          mySelectedEditorFilePath = title

          frame.removePropertyChangeListener(titleChangeListener)
        }
      }
    }
  }

  fun getView(): JComponent {
    return pane
  }

  fun setActive(value: Boolean) {
    active = value

    val color = if (value) JBUI.CurrentTheme.CustomFrameDecorations.titlePaneInfoForeground() else JBUI.CurrentTheme.CustomFrameDecorations.titlePaneInactiveInfoForeground()
    titleLabel.foreground = color

    mySelectedEditorFilePath?.setActive(value)
  }

  fun getListenerBounds(): List<RelativeRectangle> = mySelectedEditorFilePath?.getListenerBounds() ?: emptyList()
}