// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

class ToolWindowToolbarActivity : StartupActivity {
  override fun runActivity(project: Project) {
    if (!Registry.`is`("ide.new.stripes.ui")) return

    project.messageBus.connect(project).subscribe(ToolWindowManagerListener.TOPIC, ToolwindowToolbarListener(project))
  }
}

class ToolwindowToolbarListener(val project: Project) : ToolWindowManagerListener {
  override fun toolWindowShown(toolWindow: ToolWindow) {
    if (toolWindow.isVisibleOnLargeStripe) return
    toolWindow.isVisibleOnLargeStripe = true
    toolWindow.largeStripeAnchor = toolWindow.anchor
  }
}
