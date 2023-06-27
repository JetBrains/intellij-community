package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.remote.RemoteComponent
import com.intellij.driver.sdk.ui.remote.RobotService
import org.intellij.lang.annotations.Language
import java.time.Duration

fun Driver.ui(): UiRobot = UiRobot(service(RobotService::class))

class UiRobot(private val remoteRobotService: RobotService) {

  // Searching
  fun find(@Language("xpath") xpath: String, timeout: Duration = Duration.ofSeconds(5)): UiComponent {
    return find(xpath, UiComponent::class.java, timeout)
  }

  fun findAll(@Language("xpath") xpath: String): List<UiComponent> {
    return findAll(xpath, UiComponent::class.java)
  }

  // PageObject Support
  fun <T : UiComponent> find(@Language("xpath") xpath: String, uiType: Class<T>, timeout: Duration = Duration.ofSeconds(5)): T {
    waitFor(timeout, errorMessage = "Can't find uiComponent with '$xpath'") {
      findAll(xpath, uiType).size == 1
    }
    return findAll(xpath, uiType).first()
  }

  fun <T : UiComponent> findAll(@Language("xpath") xpath: String, uiType: Class<T>): List<T> {
    return remoteRobotService.findAll(xpath).map {
      uiType.getConstructor(RemoteComponent::class.java)
        .newInstance(it)
    }
  }
}