package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote

internal const val REMOTE_ROBOT_MODULE_ID = "com.jetbrains.performancePlugin/intellij.performanceTesting.remoteDriver"

@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface RobotService {
  val robot: Robot
  fun saveHierarchy(folderPath: String, fileName: String = "ui.html")
}

interface RobotServiceProvider {
  fun getDefaultRobotService(): RobotService
  fun getRobotServiceFor(obj: Any?): RobotService
}
