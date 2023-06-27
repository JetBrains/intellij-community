package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import org.intellij.lang.annotations.Language

internal const val REMOTE_ROBOT_MODULE_ID = "com.jetbrains.performancePlugin/intellij.performanceTesting.remoteDriver"

@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface RobotService : SearchContext

@Remote("com.jetbrains.performancePlugin.remotedriver.RemoteComponent",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface RemoteComponent : SearchContext {
  val component: Component
  fun findAllText(): List<TextData>
}

@Remote("java.awt.Point")
interface Point {
  val x: Int
  val y: Int
}

interface SearchContext {
  val robot: Robot
  val context: String
  fun findAll(@Language("xpath") xpath: String): List<RemoteComponent>
}