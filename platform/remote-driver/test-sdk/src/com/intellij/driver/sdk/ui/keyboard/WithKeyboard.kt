package com.intellij.driver.sdk.ui.keyboard

import com.intellij.driver.sdk.ui.remote.RobotService

interface WithKeyboard {
  val robotService: RobotService

  fun keyboard(keyboardActions: RemoteKeyboard.() -> Unit) {
    RemoteKeyboard(robotService.robot).keyboardActions()
  }
}