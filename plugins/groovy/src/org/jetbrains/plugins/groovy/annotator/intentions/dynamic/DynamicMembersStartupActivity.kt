// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance

class DynamicMembersStartupActivity : StartupActivity.DumbAware {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun runActivity(project: Project) {
    val manager = DynamicManager.getInstance(project)
    if (!manager.rootElement.getContainingClasses().isEmpty()) {
      getInstance(project).invokeLater {
        // initialize toolWindow
        DynamicToolWindowWrapper.getInstance(project).toolWindow
      }
    }
  }
}
