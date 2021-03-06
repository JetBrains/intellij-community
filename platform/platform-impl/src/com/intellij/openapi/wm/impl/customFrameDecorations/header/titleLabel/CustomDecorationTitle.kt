// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.customFrameDecorations.header.title.CustomHeaderTitle
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.docking.DockManager
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

internal class CustomDecorationTitle(val frame: JFrame): CustomHeaderTitle {
  override var onBoundsChanged: (() -> Unit)? = null
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
      isOpaque = false
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
          checkSplitters()
        }
      }
    } else {
      checkSplitters()
    }
  }

  private fun checkSplitters() {
    if(mySelectedEditorFilePath != null) return

    project?.let {
      val myDockManager = DockManager.getInstance(it)
      val dockContainer = myDockManager.getContainerFor(frame.rootPane)
      if (dockContainer is DockableEditorTabbedContainer) {
        createCustomDecoration(it)
      }
    }
  }

  private fun createCustomDecoration(it: Project) {
    val title = CustomDecorationPath.createInstance(frame)
    title.onBoundsChanged = onBoundsChanged
    title.project = it

    pane.remove(titleLabel)
    pane.add(title.view, "growx, wmin 0")
    mySelectedEditorFilePath = title
    title.setActive(active)

    frame.removePropertyChangeListener(titleChangeListener)
  }

  override val view: JComponent
    get() = pane

  override fun setActive(value: Boolean) {
    active = value

    val color = if (value) JBUI.CurrentTheme.CustomFrameDecorations.titlePaneInfoForeground() else JBUI.CurrentTheme.CustomFrameDecorations.titlePaneInactiveInfoForeground()
    titleLabel.foreground = color

    mySelectedEditorFilePath?.setActive(value)
  }

  override fun getBoundList(): List<RelativeRectangle> = mySelectedEditorFilePath?.getBoundList() ?: emptyList()
}