package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotService
import java.awt.Point

/**
 * Provides access to UI actions with the IDE under test such as keyboard and mouse input.
 */
val Driver.ui
  get(): UiRobot = UiRobot(this, service(RobotService::class))

class UiRobot(override val driver: Driver, override val robotService: RobotService) : WithKeyboard, Finder {

  fun moveMouse(point: Point) {
    robotService.robot.moveMouse(point)
  }

  fun clickMouse() {
    robotService.robot.pressMouse(RemoteMouseButton.LEFT)
    robotService.robot.releaseMouse(RemoteMouseButton.LEFT)
  }

  fun clickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.LEFT, 1)
  }

  fun doubleClickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.LEFT, 2)
  }

  fun rightClickMouse() {
    robotService.robot.pressMouse(RemoteMouseButton.RIGHT)
    robotService.robot.releaseMouse(RemoteMouseButton.RIGHT)
  }

  fun rightClickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.RIGHT, 1)
  }

  override val searchContext: SearchContext = object : SearchContext {
    override val context = ""

    override fun findAll(xpath: String): List<Component> {
      return robotService.findAll(xpath)
    }
  }
}

