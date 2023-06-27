package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import org.intellij.lang.annotations.Language

private const val DEFAULT_FIND_TIMEOUT_SECONDS = 5

internal const val REMOTE_ROBOT_MODULE_ID = "com.jetbrains.performancePlugin/intellij.performanceTesting.remoteDriver"

@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface RobotService {
  fun findAll(xpath: String): List<RemoteComponent>
  val robot: Robot
}

@Remote("com.jetbrains.performancePlugin.remotedriver.RemoteComponent",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface RemoteComponent {
  val robot: Robot
  val component: Component
  val foundByXpath: String
  fun findAll(@Language("xpath") xpath: String): List<RemoteComponent>
  fun findAllText(): List<TextData>
}

@Remote("java.awt.Point")
interface Point {
  val x: Int
  val y: Int
}