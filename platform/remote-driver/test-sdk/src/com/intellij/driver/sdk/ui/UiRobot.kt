package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.RobotService
import com.intellij.driver.sdk.ui.remote.SearchContext

fun Driver.ui(): UiRobot = UiRobot(service(RobotService::class))

class UiRobot(remoteRobotService: RobotService) : WithKeyboard, ComponentFinder {
  override val searchContext: SearchContext = remoteRobotService
}
