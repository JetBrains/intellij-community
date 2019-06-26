// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import java.util.ArrayList
import javax.swing.JComponent
import javax.swing.JFrame

class CustomDecorationPath(val frame: JFrame) : SelectedEditorFilePath() {
  private val titleChangeListener = PropertyChangeListener{
    updateProjectName()
  }

  fun setActive(value: Boolean) {
    val color = if (value) CustomFrameDecorations.titlePaneInfoForeground() else CustomFrameDecorations.titlePaneInactiveInfoForeground()
    projectLabel.foreground = color
    classTitle.foreground = color
  }

  fun getListenerBounds(): List<RelativeRectangle> {
    return if (isClipped()) {
      emptyList()
    }
    else {
      val hitTestSpots = ArrayList<RelativeRectangle>()

      hitTestSpots.addAll(getMouseInsetList(projectLabel))
      hitTestSpots.addAll(getMouseInsetList(classTitle))

      hitTestSpots
    }
  }

  override fun installListeners() {
    super.installListeners()
    frame.rootPane.addPropertyChangeListener("Window.CustomDecoration.title", titleChangeListener)
  }

  override fun unInstallListeners() {
    super.unInstallListeners()
    frame.rootPane.removePropertyChangeListener(titleChangeListener)
  }

  override fun getProjectName(project: Project): String {
    val clientProperty = frame.rootPane.getClientProperty("Window.CustomDecoration.title")
    return if(clientProperty is String) clientProperty else super.getProjectName(project)
  }

  private fun getMouseInsetList(view: JComponent,
                                mouseInsets: Int = 1): List<RelativeRectangle> {
    return listOf(
      RelativeRectangle(view, Rectangle(0, 0, mouseInsets, view.height)),
      RelativeRectangle(view, Rectangle(0, 0, view.width, mouseInsets)),
      RelativeRectangle(view,
                        Rectangle(0, view.height - mouseInsets, view.width, mouseInsets)),
      RelativeRectangle(view,
                        Rectangle(view.width - mouseInsets, 0, mouseInsets, view.height))
    )
  }
}