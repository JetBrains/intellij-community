// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for the Modules tool window that displays all JPS modules in the project.
 */
class ModulesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val modulesPanel = ModulesPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(modulesPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override suspend fun isApplicableAsync(project: Project): Boolean {
        return IntelliJProjectUtil.isIntelliJPlatformProject(project) &&
               Registry.`is`("devkit.modules.toolwindow.enabled", false)
    }
}
