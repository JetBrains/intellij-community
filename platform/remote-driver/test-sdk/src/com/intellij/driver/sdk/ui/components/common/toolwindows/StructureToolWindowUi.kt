package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.rdTarget
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

class StructureToolWindowUi(data: ComponentData) : UiComponent(data) {
  fun waitAndGetStructureTree(message: String? = null, waitForText: ((UiText) -> Boolean)? = null): JTreeUiComponent {
    val structureTree = structureTree.waitFound(10.seconds)
    structureTree.expandAll()
    waitForText?.let { structureTree.waitAnyTexts(message = message, timeout = 10.seconds, predicate = it) }
    return structureTree
  }

  private val structureTree
    get() = tree().apply { replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class, rdTarget = component.rdTarget)) }

  private val header
    get() = x(".//div[@class='BaseLabel' and @accessiblename='Structure']")
  val tabs: List<UiComponent>
    get() {
      structureTree.waitFound(5.seconds)
      return xx("//div[@class='ContentTabLabel']").list()
    }

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