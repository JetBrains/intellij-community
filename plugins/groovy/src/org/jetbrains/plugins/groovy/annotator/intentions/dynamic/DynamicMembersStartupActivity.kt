// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager

private class DynamicMembersStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val manager = project.serviceAsync<DynamicManager>()
    if (!manager.rootElement.getContainingClasses().isEmpty()) {
      project.serviceAsync<ToolWindowManager>().invokeLater {
        // initialize toolWindow
        @Suppress("SimplifiableServiceRetrieving")
        project.service<DynamicToolWindowWrapper>().toolWindow
      }
    }
  }
}
