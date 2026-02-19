package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Robot
import com.intellij.driver.sdk.ui.remote.RobotProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import com.intellij.driver.sdk.ui.remote.SwingHierarchyService
import java.awt.Point

/**
 * Provides access to UI actions with the IDE under test such as keyboard and mouse input.
 */
val Driver.ui
  get(): UiRobot {
    val searchService = SearchService(service(SwingHierarchyService::class), this)
    return UiRobot(this, searchService, DefaultSearchContext(searchService), RobotProvider(this))
  }

open class UiRobot(
  override val driver: Driver,
  override val searchService: SearchService,
  override val searchContext: SearchContext,
  override val robotProvider: RobotProvider
) : WithKeyboard, Finder {

  val robot: Robot
    get() = robotProvider.defaultRobot

  fun moveMouse(point: Point) {
    robot.moveMouse(point)
  }

  fun clickMouse() {
    robot.pressMouse(RemoteMouseButton.LEFT)
    robot.releaseMouse(RemoteMouseButton.LEFT)
  }

  fun clickMouse(point: Point) {
    robot.click(point, RemoteMouseButton.LEFT, 1)
  }

  fun doubleClickMouse(point: Point) {
    robot.click(point, RemoteMouseButton.LEFT, 2)
  }

  fun rightClickMouse() {
    robot.pressMouse(RemoteMouseButton.RIGHT)
    robot.releaseMouse(RemoteMouseButton.RIGHT)
  }

  fun rightClickMouse(point: Point) {
    robot.click(point, RemoteMouseButton.RIGHT, 1)
  }

}

class DefaultSearchContext(val searchService: SearchService) : SearchContext {
  override val context = ""

  override fun findAll(xpath: String): List<Component> {
    return searchService.findAll(xpath)
  }
}
