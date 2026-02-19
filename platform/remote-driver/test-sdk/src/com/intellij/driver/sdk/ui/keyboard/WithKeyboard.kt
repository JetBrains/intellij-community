package com.intellij.driver.sdk.ui.keyboard

import com.intellij.driver.sdk.ui.remote.RobotProvider

interface WithKeyboard {
  val robotProvider: RobotProvider

  fun keyboard(keyboardActions: RemoteKeyboard.() -> Unit) {
    RemoteKeyboard(robotProvider.defaultRobot).keyboardActions()
  }
}