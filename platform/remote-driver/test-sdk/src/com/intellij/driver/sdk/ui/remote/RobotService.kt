package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.model.TextDataList
import org.intellij.lang.annotations.Language

internal const val REMOTE_ROBOT_MODULE_ID = "com.jetbrains.performancePlugin/intellij.performanceTesting.remoteDriver"

@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface RobotService {
  val robot: Robot
  fun findAll(@Language("xpath") xpath: String): List<Component>
  fun findAll(@Language("xpath") xpath: String, component: Component): List<Component>
  fun findAllText(component: Component): TextDataList
}