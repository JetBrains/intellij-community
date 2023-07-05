package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.model.TextData
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.*
import java.awt.Point


data class ComponentWrapper(val driver: Driver, val robotService: RobotService, val component: Component, val foundByXpath: String)

@Suppress("MemberVisibilityCanBePrivate")
open class UiComponent(componentData: ComponentWrapper) : WithKeyboard, Finder {
  override val driver: Driver = componentData.driver
  override val robotService: RobotService = componentData.robotService
  val component = componentData.component
  private val foundByXpath = componentData.foundByXpath


  override val searchContext: SearchContext = object : SearchContext {
    override val context = foundByXpath
    override fun findAll(xpath: String): List<Component> {
      return robotService.findAll(xpath, component)
    }
  }

  // Search Text Locations
  fun findText(text: String): UiText {
    return findAllText {
      it.text == text
    }.single()
  }

  fun hasText(text: String): Boolean {
    return findAllText {
      it.text == text
    }.isNotEmpty()
  }

  fun findText(predicate: (TextData) -> Boolean): UiText {
    return robotService.findAllText(component).single(predicate).let { UiText(this, it) }
  }

  fun hasText(predicate: (TextData) -> Boolean): Boolean {
    return robotService.findAllText(component).any(predicate)
  }

  fun findAllText(predicate: (TextData) -> Boolean): List<UiText> {
    return robotService.findAllText(component).filter(predicate).map { UiText(this, it) }
  }

  fun findAllText(): List<UiText> {
    return robotService.findAllText(component).map { UiText(this, it) }
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
}