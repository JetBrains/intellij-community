// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqsCompose
import org.jetbrains.jewel.foundation.JewelFlags

class LockReqsToolWindowFactoryJewel : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.addComposeTab(DevKitBundle.message("tab.title.locking.requirements")) { LockReqsCompose(project) }
  }
}

