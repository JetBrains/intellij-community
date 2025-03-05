package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.xQuery

fun IdeaFrameUI.projectView(
  locator: QueryBuilder.() -> String = { componentWithChild(byType("com.intellij.toolWindow.InternalDecoratorImpl"), byType("com.intellij.ide.projectView.impl.ProjectViewTree")) },
  action: ProjectViewToolWindowUi.() -> Unit = {},
): ProjectViewToolWindowUi = x(ProjectViewToolWindowUi::class.java, locator).apply(action)

class ProjectViewToolWindowUi(data: ComponentData) : UiComponent(data) {
  val projectViewTree = tree(xQuery { byType("com.intellij.ide.projectView.impl.ProjectViewTree") })

  fun expandAll() = x("//div[@myicon='expandAll.svg']").waitVisible().click()
}
