package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import kotlin.time.Duration.Companion.seconds

fun Finder.projectViewToolWindow(): ProjectViewToolWindowUi =
  x(ProjectViewToolWindowUi::class.java) {
    byAccessibleName("Project Tool Window")
  }

fun Finder.showProjectViewButton() = x { and (byAccessibleName("Project"), byClass("SquareStripeButton")) }

fun Finder.openProjectViewToolWindow(): ProjectViewToolWindowUi {
  return runCatching { projectViewToolWindow().waitFound(3.seconds) }.getOrElse {
    showProjectViewButton().click()
    projectViewToolWindow().waitFound(3.seconds)
  }
}

class ProjectViewToolWindowUi(data: ComponentData) : JTreeUiComponent(data) {
  fun expandAll() = x("//div[@myicon='expandAll.svg']").waitVisible().click()
}



