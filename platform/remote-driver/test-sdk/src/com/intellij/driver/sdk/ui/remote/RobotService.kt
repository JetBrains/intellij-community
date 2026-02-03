package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.RdTarget

internal const val REMOTE_ROBOT_MODULE_ID = "com.jetbrains.performancePlugin/intellij.performanceTesting.remoteDriver"

@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface RobotService {
  val robot: Robot
  fun saveHierarchy(folderPath: String, fileName: String)
}

class RobotProvider(private val driver: Driver) {
  private val defaultRobotService: RobotService
    get() = driver.service(RobotService::class)

  private val backendRobotService: RobotService
    get() = driver.service(RobotService::class, RdTarget.BACKEND)

  val defaultRobot: Robot
    get() = defaultRobotService.robot

  fun getRobotFor(obj: Any?): Robot {
    if (obj !is RefWrapper) {
      return defaultRobot
    }
    return when (obj.getRef().rdTarget()) {
      RdTarget.FRONTEND, RdTarget.DEFAULT -> defaultRobotService
      RdTarget.BACKEND -> backendRobotService
    }.robot
  }

  fun saveHierarchy(folderPath: String, fileName: String = "ui.html") = defaultRobotService.saveHierarchy(folderPath, fileName)
}
