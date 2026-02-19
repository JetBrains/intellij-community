// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.diagnostic.runActivity
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.PlatformUtils
import training.lang.LangManager
import training.util.LEARNING_PANEL_OPENED_IN
import training.util.findLanguageSupport

const val LEARN_TOOL_WINDOW_ID: String = "Learn"

internal class LearnToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    return !PlatformUtils.isWebStorm()
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.isShowStripeButton = findLanguageSupport(toolWindow.project) != null
  }

  override val anchor: ToolWindowAnchor?
    get() {
      // calling LangManager can slow down start-up - measure it
      runActivity("learn tool window anchor setting") {
        return LangManager.getInstance().getLangSupportBean()?.getLearnToolWindowAnchor()
      }
    }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val currentBuildStr = ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot()
    PropertiesComponent.getInstance().setValue(LEARNING_PANEL_OPENED_IN, currentBuildStr)
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(LearnToolWindow(project, toolWindow), null, false)
    content.isCloseable = false
    contentManager.addContent(content)
  }
}
