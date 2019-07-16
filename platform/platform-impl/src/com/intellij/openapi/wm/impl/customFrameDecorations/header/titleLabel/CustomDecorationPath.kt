// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JComponent
import javax.swing.JFrame

class CustomDecorationPath(val frame: JFrame, onBoundsChanged: () -> Unit) : SelectedEditorFilePath(onBoundsChanged) {

  private val titleChangeListener = PropertyChangeListener{
    updateProjectName()
  }

  fun setActive(value: Boolean) {
    val color = if (value) CustomFrameDecorations.titlePaneInfoForeground() else CustomFrameDecorations.titlePaneInactiveInfoForeground()

    components.forEach {
      it.component.foreground = color
    }
  }

  fun getListenerBounds(): List<RelativeRectangle> {
    return if (!isClipped()) {
      emptyList()
    }
    else {
      val hitTestSpots = ArrayList<RelativeRectangle>()
      hitTestSpots.addAll(getMouseInsetList(getView()))
      hitTestSpots
    }
  }

  override fun installListeners() {
    super.installListeners()
    frame.addPropertyChangeListener("title", titleChangeListener)
  }

  override fun unInstallListeners() {
    super.unInstallListeners()
    frame.removePropertyChangeListener(titleChangeListener)
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