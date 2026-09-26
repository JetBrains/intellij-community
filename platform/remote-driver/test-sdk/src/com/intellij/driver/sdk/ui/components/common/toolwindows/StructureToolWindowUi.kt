package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.contentTabLabel
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.tree
import org.intellij.lang.annotations.Language
import java.awt.Point
import kotlin.time.Duration.Companion.seconds


fun Finder.structureToolWindow(@Language("xpath") xpath: String? = null): StructureToolWindowUi = x(
  xpath
  ?: "//div[@class='InternalDecoratorImpl' and (@accessiblename='Structure Tool Window' or @accessiblename='Logical Tool Window' or @accessiblename='Physical Tool Window')]",
  StructureToolWindowUi::class.java
)

fun Finder.structureToolWindowButton(@Language("xpath") xpath: String? = null): StripeButtonUi = x(
  xpath ?: "//div[@class='SquareStripeButton' and @accessiblename='Structure']",
  StripeButtonUi::class.java
)

class StructureToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  fun waitAndGetStructureTree(message: String? = null, waitForText: ((UiText) -> Boolean)? = null): JTreeUiComponent {
    val structureTree = structureTree.waitFound(10.seconds)
    structureTree.expandAll()
    waitForText?.let { structureTree.waitAnyTexts(message = message, timeout = 10.seconds, predicate = it) }
    return structureTree
  }
  fun waitExpandedAndGetStructureTree(message: String? =  null, expandPath: List<String>? = null, waitForText: ((UiText) -> Boolean)? = null): JTreeUiComponent {
    val structureTree = structureTree.waitFound(10.seconds)
    step("Expand path: $expandPath") {
      expandPath?.toTypedArray()?.let {
        structureTree.expandPath(*it, fullMatch = false)
      }
    }
    waitForText?.let { structureTree.waitAnyTexts(message = message, timeout = 10.seconds, predicate = it) }
    return structureTree
  }

  private val structureTree
    get() = tree()

  private val header
    get() = x(".//div[@class='BaseLabel' and @accessiblename='Structure']")
  val tabs: List<UiComponent>
    get() {
      structureTree.waitFound(5.seconds)
      return xx("//div[@class='ContentTabLabel']").list()
    }

  fun hasMultipleTabs(): Boolean = tabs.size >= 2 || hiddenTabsButton.present()

  fun selectTab(tabName: String) {
    val visibleTab = contentTabLabel(tabName)
    if (visibleTab.present()) {
      step("Select visible Structure tab '$tabName'") {
        visibleTab.click()
      }
      return
    }

    step("Select hidden Structure tab '$tabName'") {
      hiddenTabsButton.click()
      driver.ui.popup().list().clickItem(tabName)
    }
  }

  private val hiddenTabsButton: ActionButtonUi
    get() = toolWindowHeader.actionButton { byAccessibleName("Show Hidden Tabs") }

  fun expandViewOptions() {
    viewOptionsButton.click()
  }

  fun withViewOptions(action: () -> Unit) {
    expandViewOptions()
    try {
      action()
    }
    finally {
      collapseViewOptions()
    }
  }

  fun collapseViewOptions() {
    header.waitFound(5.seconds).click()
  }

  val viewOptionsButton: ActionButtonUi
    get() {
      val actionButtenXpath = "//div[@class='ActionButton' and @myicon='show.svg']"
      if (xx(actionButtenXpath).list().isEmpty()) {
        actionMenuAppearance()
      }
      return x(actionButtenXpath, ActionButtonUi::class.java)
    }

  private fun actionMenuAppearance() {
    val headerComponent = header.component
    moveMouse(Point(headerComponent.x, headerComponent.y))
  }
}
