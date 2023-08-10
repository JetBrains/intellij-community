package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotService
import java.awt.Point

fun Driver.ui(): UiRobot = UiRobot(this, service(RobotService::class))

class UiRobot(override val driver: Driver, override val robotService: RobotService) : WithKeyboard, Finder {

  fun moveMouse(point: Point) {
    robotService.robot.moveMouse(point)
  }

  fun clickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.LEFT, 1)
  }

  fun doubleClickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.LEFT, 2)
  }

  fun rightClickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.RIGHT, 1)
  }

  override val searchContext: SearchContext = object : SearchContext {
    override val context = "root"
    override fun findAll(xpath: String): List<Component> {
      return robotService.findAll(xpath)
    }
  }
}

