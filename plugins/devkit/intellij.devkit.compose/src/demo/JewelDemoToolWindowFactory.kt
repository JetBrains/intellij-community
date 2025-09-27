package com.intellij.devkit.compose.demo

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.devkit.compose.demo.releasessample.ReleasesSampleCompose
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.foundation.JewelFlags
import javax.swing.JComponent

internal class JewelDemoToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    // Enable custom popup rendering to use JBPopup instead of the default Compose implementation
    JewelFlags.useCustomPopupRenderer = true

    toolWindow.addComposeTab(DevkitComposeBundle.message("jewel.tw.tab.title.components")) { ComponentShowcaseTab(project) }

    toolWindow.addComposeTab(DevkitComposeBundle.message("jewel.tw.tab.title.releases.demo")) { ReleasesSampleCompose(project) }

    toolWindow.addSwingTab(SwingComparisonTabPanel(), DevkitComposeBundle.message("jewel.tw.tab.title.swing.comparison"))

    toolWindow.addComposeTab(DevkitComposeBundle.message("jewel.tw.tab.title.scrollbars.sample")) { ScrollbarsShowcaseTab() }
  }

  private fun ToolWindow.addSwingTab(component: JComponent, @TabTitle title: String) {
    val manager = contentManager
    val tabContent = manager.factory.createContent(component, title, true)
    tabContent.isCloseable = false
    manager.addContent(tabContent)
  }
}
