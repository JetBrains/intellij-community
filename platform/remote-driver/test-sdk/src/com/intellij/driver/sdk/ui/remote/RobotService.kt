package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import org.intellij.lang.annotations.Language


private const val DEFAULT_FIND_TIMEOUT_SECONDS = 5
@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService", plugin = "com.jetbrains.performancePlugin")
interface RobotService {
  fun findAll(xpath: String): List<RemoteComponent>
  val robot: Robot
}

@Remote("com.jetbrains.performancePlugin.remotedriver.RemoteComponent", plugin = "com.jetbrains.performancePlugin")
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