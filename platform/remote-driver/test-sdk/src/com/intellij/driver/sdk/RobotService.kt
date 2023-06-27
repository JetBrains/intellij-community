package com.intellij.driver.sdk

import com.intellij.driver.client.Remote
import org.intellij.lang.annotations.Language


// client

@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService", plugin = "com.jetbrains.performancePlugin")
interface RobotService {
  fun find(@Language("xpath") xpath: String): ComponentFixture

  fun find(@Language("xpath") xpath: String, inComponent: Component): ComponentFixture
}

@Remote("com.jetbrains.performancePlugin.remotedriver.ComponentFixture", plugin = "com.jetbrains.performancePlugin")
interface ComponentFixture {
  fun getComponent(): Component
  fun click()
}

@Remote("java.awt.Component")
interface Component {

}