// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.toolWindow.StripeButtonManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.xNext.toolbar.XNextBar
import com.intellij.toolWindow.xNext.toolbar.actions.toolbar.XNextActionToolbar
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.JComponent

internal class XNextToolWindowButtonManager(paneId: String, isPrimary: Boolean) :
  ToolWindowPaneNewButtonManager(paneId, isPrimary) {

  constructor(paneId: String) : this(paneId, true)

  private val xNextToolbar = XNextToolWindowToolbar(paneId, isPrimary)

  override fun setupToolWindowPane(pane: JComponent) {
    xNextToolbar.topStripe.bottomAnchorDropAreaComponent = pane
    xNextToolbar.bottomStripe.bottomAnchorDropAreaComponent = pane
    super.setupToolWindowPane(pane)
  }

  override fun createStripeButton(toolWindow: ToolWindowImpl, info: WindowInfo, task: RegisterToolWindowTask?): StripeButtonManager {
    val manager = super.createStripeButton(toolWindow)
    if(toolWindow.id == "Project" || toolWindow.id == "Version Control") {
      xNextToolbar.topStripe.addButton(manager)
    } else if(toolWindow.id == "Database" || toolWindow.id == "Structure" || toolWindow.id == "AIAssistant" || toolWindow.id == "Debug") {
      xNextToolbar.bottomStripe.addButton(manager)
    }
    return super.createStripeButton(toolWindow, info, task)
  }

/*  private fun createXNextStripeButton(toolWindow: ToolWindowImpl): StripeButtonManager {
    return object : StripeButtonManager {
      override val id: String = toolWindow.id
      override val toolWindow: ToolWindowImpl = toolWindow

    }
  }*/


  override fun getStripeFor(anchor: ToolWindowAnchor, isSplit: Boolean?): AbstractDroppableStripe {
    //при драге определяем страйп над которым шевелимся

    return super.getStripeFor(anchor, isSplit)
  }

  override fun wrapWithControls(pane: ToolWindowPane): JComponent {
    return super.wrapWithControls(pane).apply {
      add(XNextBar().apply{
        add(BorderLayoutPanel().apply {
          val toolbar = XNextActionToolbar()
          toolbar.setTargetComponent(this)
          add(toolbar.component.apply {
            toolbar.component.isOpaque = true
          }, BorderLayout.CENTER)
        })

      }, BorderLayout.SOUTH)
    }
  }
}