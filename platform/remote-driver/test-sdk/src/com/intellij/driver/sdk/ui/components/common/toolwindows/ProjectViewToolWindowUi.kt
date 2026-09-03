package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.xQuery

fun IdeaFrameUI.projectView(
  locator: QueryBuilder.() -> String = {
    componentWithChild(
      byType("com.intellij.toolWindow.InternalDecoratorImpl"),
      or(
        byType("com.intellij.ide.projectView.impl.ProjectViewTree"),
        byType("com.intellij.platform.projectView.frontend.impl.FrontendProjectViewTree"),
      )
    )
  },
  action: ProjectViewToolWindowUi.() -> Unit = {},
): ProjectViewToolWindowUi = x(ProjectViewToolWindowUi::class.java, locator).apply(action)

class ProjectViewToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  val projectViewTree = tree(xQuery {
    or(
      byType("com.intellij.ide.projectView.impl.ProjectViewTree"),
      byType("com.intellij.platform.projectView.frontend.impl.FrontendProjectViewTree"),
    )
  })

  val expandAllIcon = x("//div[@myicon='expandAll.svg']")
  fun expandAll() = x("//div[@myicon='expandAll.svg']").click()

  val collapseAllIcon = x("//div[@myicon='collapseAll.svg']")
  fun collapseAll() = x("//div[@myicon='collapseAll.svg']").click()

  val selectOpenedFileIcon = x("//div[@myicon='locate.svg']")
  fun selectOpenedFile() = x("//div[@myicon='locate.svg']").click()
}
