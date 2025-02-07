package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.rdTarget
import org.intellij.lang.annotations.Language
import java.awt.Point
import kotlin.time.Duration.Companion.seconds


fun Finder.structureToolWindow(@Language("xpath") xpath: String? = null) = x(
  xpath ?: "//div[@class='InternalDecoratorImpl' and (@accessiblename='Structure Tool Window' or @accessiblename='Logical Tool Window' or @accessiblename='Physical Tool Window')]",
  StructureToolWindowUi::class.java
)

fun Finder.structureToolWindowButton(@Language("xpath") xpath: String? = null) = x(
  xpath ?: "//div[@class='SquareStripeButton' and @accessiblename='Structure']"
)

class StructureToolWindowUi(data: ComponentData) : UiComponent(data) {

  val structureTree
    get() = tree().apply { replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class, rdTarget = component.rdTarget)) }

  private val header
    get() = x(".//div[@class='BaseLabel' and @accessiblename='Structure']")

  fun expandViewOptions() {
    viewOptionsButton.waitFound(5.seconds).click()
  }
  fun collapseViewOptions() {
    header.waitFound(5.seconds).click()
  }

  val viewOptionsButton: ActionButtonUi
    get() {
      actionMenuAppearance()
      return x(
        "//div[@class='ActionButton' and @myicon='show.svg']",
        ActionButtonUi::class.java
      )
    }

  private fun actionMenuAppearance(){
    val headerComponent = header.component
    moveMouse(Point(headerComponent.x, headerComponent.y))
  }
}