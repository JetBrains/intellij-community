// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.diagnostic.runActivity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactoryEx
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.util.containers.ContainerUtil
import training.lang.LangManager
import training.util.findLanguageSupport

internal class LearnToolWindowFactory : ToolWindowFactoryEx, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    super.init(toolWindow)
    val project = (toolWindow as? ToolWindowEx)?.project ?: return
    toolWindow.isShowStripeButton = findLanguageSupport(project) != null
  }

  override fun getAnchor(): ToolWindowAnchor? {
    // calling LangManager can slow down start-up - measure it
    runActivity("learn tool window anchor setting") {
      return LangManager.getInstance().getLangSupport()?.getToolWindowAnchor()
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val learnToolWindow = LearnToolWindow(project, toolWindow)
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(learnToolWindow, null, false)
    content.isCloseable = false
    contentManager.addContent(content)

    learnWindowPerProject[project] = learnToolWindow
  }

  companion object {
    const val LEARN_TOOL_WINDOW = "Learn"
    val learnWindowPerProject: MutableMap<Project, LearnToolWindow> = ContainerUtil.createWeakMap()
  }
}

