package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.model.transport.RemoteMouseButton
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.RobotService
import com.intellij.driver.sdk.ui.remote.SearchContext
import java.awt.Point

fun Driver.ui(): UiRobot = UiRobot(service(RobotService::class))

class UiRobot(remoteRobotService: RobotService) : WithKeyboard, ComponentFinder {
  override val searchContext: SearchContext = remoteRobotService

  fun moveMouse(point: Point) {
    searchContext.robot.moveMouse(point)
  }

  fun clickMouse(point: Point) {
    searchContext.robot.click(point, RemoteMouseButton.LEFT, 1)
  }

  fun doubleClickMouse(point: Point) {
    searchContext.robot.click(point, RemoteMouseButton.LEFT, 2)
  }

  fun rightClickMouse(point: Point) {
    searchContext.robot.click(point, RemoteMouseButton.RIGHT, 1)
  }
}
