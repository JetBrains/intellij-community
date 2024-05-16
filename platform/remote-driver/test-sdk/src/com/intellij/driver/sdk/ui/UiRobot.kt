package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.RdTarget
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.RobotService
import com.intellij.driver.sdk.ui.remote.RobotServiceProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import com.intellij.driver.sdk.ui.remote.SwingHierarchyService
import java.awt.Point

/**
 * Provides access to UI actions with the IDE under test such as keyboard and mouse input.
 */
val Driver.ui
  get(): UiRobot {
    val searchService = SearchService(service(SwingHierarchyService::class), this)
    return UiRobot(this, searchService, DefaultSearchContext(searchService), RemDevRobotServiceProvider(this))
  }

open class UiRobot(
  override val driver: Driver,
  override val searchService: SearchService,
  override val searchContext: SearchContext,
  override val robotServiceProvider: RobotServiceProvider
) : WithKeyboard, Finder {

  override val robotService: RobotService
    get() = robotServiceProvider.getDefaultRobotService()

  fun moveMouse(point: Point) {
    robotService.robot.moveMouse(point)
  }

  fun clickMouse() {
    robotService.robot.pressMouse(RemoteMouseButton.LEFT)
    robotService.robot.releaseMouse(RemoteMouseButton.LEFT)
  }

  fun clickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.LEFT, 1)
  }

  fun doubleClickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.LEFT, 2)
  }

  fun rightClickMouse() {
    robotService.robot.pressMouse(RemoteMouseButton.RIGHT)
    robotService.robot.releaseMouse(RemoteMouseButton.RIGHT)
  }

  fun rightClickMouse(point: Point) {
    robotService.robot.click(point, RemoteMouseButton.RIGHT, 1)
  }

}

class DefaultSearchContext(val searchService: SearchService) : SearchContext {
  override val context = ""

  override fun findAll(xpath: String): List<Component> {
    return searchService.findAll(xpath)
  }
}

class RemDevRobotServiceProvider(private val driver: Driver) : RobotServiceProvider {
  private val frontendRobotService: RobotService
    get() = driver.service(RobotService::class)
  private val backendRobotService: RobotService
    get() = driver.service(RobotService::class, RdTarget.BACKEND)

  override fun getDefaultRobotService(): RobotService {
    return frontendRobotService
  }

  override fun getRobotServiceFor(obj: Any?): RobotService {
    if (obj !is RefWrapper) {
      return getDefaultRobotService()
    }
    return when (obj.getRef().rdTarget()) {
      RdTarget.FRONTEND, RdTarget.DEFAULT -> frontendRobotService
      RdTarget.BACKEND -> backendRobotService
    }
  }
}