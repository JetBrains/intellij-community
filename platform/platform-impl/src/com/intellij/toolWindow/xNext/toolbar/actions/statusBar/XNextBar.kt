// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.statusBar


import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.project.Project
import com.intellij.toolWindow.xNext.toolbar.actions.toolbar.XNextActionToolbar
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

internal class XNextBar : JBPanel<JBPanel<*>>() {
  private val toolbar = XNextActionToolbar()
  private val rightPane = XNextStatusBarWidgetPane()
  private val leftPane = JBPanel<JBPanel<*>>()

  fun init(project: Project) {
    rightPane.init(project)
  }

  init {
    border = JBUI.Borders.empty()
    toolbar.setTargetComponent(this)

    add(toolbar.component.apply {
      border = JBUI.Borders.empty()
      isOpaque = true
    })

    add(leftPane)
    add(rightPane.component)

    InternalUICustomization.getInstance()?.installBackgroundUpdater(this)
  }

  override fun doLayout() {
    super.doLayout()

    layoutComponent(toolbar.component) { (width - it)/2 }
    layoutComponent(leftPane) { 0 }
    layoutComponent(rightPane.component) { width - it }
  }

  private fun layoutComponent(component: JComponent, getX: (Int) -> Int) {
    val size = component.preferredSize
    val y = (height - size.height) / 2
    component.setBounds(getX(size.width), y, size.width, size.height)
  }
}

