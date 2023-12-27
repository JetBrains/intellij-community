package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.model.TextData
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT_SECONDS
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.SearchContext
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotService
import com.intellij.driver.sdk.waitFor
import java.awt.Point
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


data class ComponentData(val xpath: String,
                         val driver: Driver,
                         val robotService: RobotService,
                         val parentSearchContext: SearchContext,
                         val foundComponent: Component?,
                         val timeout: Duration = DEFAULT_FIND_TIMEOUT_SECONDS.seconds)

open class UiComponent(private val data: ComponentData) : Finder, WithKeyboard {
  val component: Component by lazy {
    data.foundComponent ?: findThisComponent()
  }

  private fun findThisComponent(): Component {
    waitFor(data.timeout,
            errorMessage = "Can't find component with '${data.xpath}' in ${searchContext.context}") {
      data.parentSearchContext.findAll(data.xpath).size == 1
    }
    return data.parentSearchContext.findAll(data.xpath).first()
  }

  override val driver: Driver = data.driver
  override val robotService: RobotService = data.robotService

  override val searchContext: SearchContext = object : SearchContext {
    override val context: String = data.parentSearchContext.context + data.xpath

    override fun findAll(xpath: String): List<Component> {
      return robotService.findAll(xpath, component)
    }
  }

  // Search Text Locations
  fun findText(text: String): UiText {
    val allTexts = findAllText()
    val filteredTexts = allTexts.filter { it.text == text }
    if (filteredTexts.isEmpty()) {
      throw AssertionError("No '$text' found. Available texts are:\n${allTexts.joinToString("\n") { it.text }}")
    }
    if (filteredTexts.size > 1) {
      throw AssertionError("Found ${filteredTexts.size} texts '$text', expected 1")
    }
    return filteredTexts.first()
  }

  fun hasText(text: String): Boolean {
    return findAllText {
      it.text == text
    }.isNotEmpty()
  }

  fun findText(predicate: (TextData) -> Boolean): UiText {
    return robotService.findAllText(component).single(predicate).let { UiText(this, it) }
  }

  fun present(): Boolean {
   return robotService.findAll(data.xpath).isNotEmpty()
  }

  fun notPresent(): Boolean {
    return robotService.findAll(data.xpath).isEmpty()
  }

  fun hasText(predicate: (TextData) -> Boolean): Boolean {
    return robotService.findAllText(component).any(predicate)
  }

  fun isVisible(): Boolean = component.isVisible()

  fun findAllText(predicate: (TextData) -> Boolean): List<UiText> {
    return robotService.findAllText(component).filter(predicate).map { UiText(this, it) }
  }

  fun findAllText(): List<UiText> {
    return robotService.findAllText(component).map { UiText(this, it) }
  }

  fun hasVisibleComponent(component: UiComponent): Boolean {
    val components = searchContext.findAll(component.data.xpath)
    if (components.isEmpty()) return false
    return components.any { it.isVisible() }
  }

  // Mouse
  fun click(point: Point? = null) {
    if (point != null) {
      robotService.robot.click(component, point)
    }
    else {
      robotService.robot.click(component)
    }
  }

  fun doubleClick(point: Point? = null) {
    if (point != null) {
      robotService.robot.click(component, point, RemoteMouseButton.LEFT, 2)
    }
    else {
      robotService.robot.doubleClick(component)
    }
  }

  fun rightClick(point: Point? = null) {
    if (point != null) {
      robotService.robot.click(component, point, RemoteMouseButton.RIGHT, 1)
    }
    else {
      robotService.robot.rightClick(component)
    }
  }

  fun click(button: RemoteMouseButton, count: Int) {
    robotService.robot.click(component, button, count)
  }

  fun moveMouse() {
    robotService.robot.moveMouse(component)
  }

  fun moveMouse(point: Point) {
    robotService.robot.moveMouse(component, point)
  }

  fun withTimeout(duration: Duration): UiComponent {
    return UiComponent(this.data.copy(timeout = duration))
  }
}