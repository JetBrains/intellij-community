package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.shouldBe
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit

fun Driver.runCurrentRunConfiguration() {
  ideFrame {
    step("Run selected run configuration") {
      mainToolbar.runButton
        .shouldBe("The run button should be enabled") { isEnabled() }
        .click()
    }
  }
}

fun getScreenWorkingArea(): Rectangle {
  val graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().single().getDefaultConfiguration()
  val bounds = graphicsConfiguration.bounds
  val insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)

  return Rectangle(bounds.x + insets.left,
                   bounds.y + insets.top,
                   bounds.width - insets.left - insets.right,
                   bounds.height - insets.top - insets.bottom)
}
