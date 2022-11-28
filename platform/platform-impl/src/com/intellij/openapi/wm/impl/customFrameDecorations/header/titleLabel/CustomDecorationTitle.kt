// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

internal class CustomDecorationTitle(private val frame: JFrame) : CustomHeaderTitle {
  override var onBoundsChanged: (() -> Unit)? = null
  private var selectedEditorFilePath: CustomDecorationPath? = null
  private var active = false

  private val titleChangeListener = PropertyChangeListener {
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
    if (project == null) {
      if (frame is IdeFrame) {
        frame.project?.let {
          project = it
          checkSplitters()
        }
      }
    }
    else {
      checkSplitters()
    }
  }

  private fun checkSplitters() {
    if (selectedEditorFilePath != null) {
      return
    }

    project?.let { project ->
      val dockManager = DockManager.getInstance(project)
      val dockContainer = dockManager.getContainerFor(frame.rootPane) { it is DockableEditorTabbedContainer }
      if (dockContainer is DockableEditorTabbedContainer) {
        createCustomDecoration(project)
      }
    }
  }

  private fun createCustomDecoration(project: Project) {
    val title = CustomDecorationPath.createInstance(frame)
    title.onBoundsChanged = onBoundsChanged
    title.project = project

    pane.remove(titleLabel)
    pane.add(title.view, "growx, wmin 0")
    selectedEditorFilePath = title
    title.setActive(active)

    frame.removePropertyChangeListener(titleChangeListener)
  }

  override val view: JComponent
    get() = pane

  override fun setActive(value: Boolean) {
    active = value

    val color = if (value) {
      JBUI.CurrentTheme.CustomFrameDecorations.titlePaneInfoForeground()
    }
    else {
      JBUI.CurrentTheme.CustomFrameDecorations.titlePaneInactiveInfoForeground()
    }
    titleLabel.foreground = color
    selectedEditorFilePath?.setActive(value)
  }

  override fun getBoundList(): List<RelativeRectangle> = selectedEditorFilePath?.getBoundList() ?: emptyList()
}