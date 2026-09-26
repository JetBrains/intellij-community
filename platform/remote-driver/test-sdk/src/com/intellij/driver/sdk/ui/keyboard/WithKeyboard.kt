package com.intellij.driver.sdk.ui.keyboard

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.jdk.isRemoteMac
import com.intellij.driver.sdk.ui.remote.RobotProvider

interface WithKeyboard {
  val driver: Driver
  val robotProvider: RobotProvider

  fun keyboard(keyboardActions: RemoteKeyboard.() -> Unit) {
    RemoteKeyboard(robotProvider.defaultRobot, { driver.isRemoteMac }).keyboardActions()
  }
}