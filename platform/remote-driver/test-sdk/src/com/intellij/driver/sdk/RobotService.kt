package com.intellij.driver.sdk

import com.intellij.driver.client.Remote
import org.intellij.lang.annotations.Language



@Remote("com.jetbrains.performancePlugin.remotedriver.RobotService", plugin = "com.jetbrains.performancePlugin")
interface RobotService {
  fun find(@Language("xpath") xpath: String): ComponentFixture
  fun findAll(@Language("xpath") xpath: String): List<ComponentFixture>
  val remoteRobot: Robot
}

@Remote("com.jetbrains.performancePlugin.remotedriver.ComponentFixture", plugin = "com.jetbrains.performancePlugin")
interface ComponentFixture {
  val component: Component
  fun click()
  fun find(@Language("xpath") xpath: String): ComponentFixture
  fun findAll(@Language("xpath") xpath: String): List<ComponentFixture>

  fun findAllText(): List<TextData>
}

@Remote("java.awt.Component")
interface Component {
  val x: Int
  val y: Int
  val width: Int
  val height: Int
}

@Remote("org.assertj.swing.core.Robot", plugin = "com.jetbrains.performancePlugin")
interface Robot {

}

@Remote("com.jetbrains.performancePlugin.remotedriver.dataextractor.server.TextData", plugin = "com.jetbrains.performancePlugin")
interface TextData {
  val text: String
  val point: Point
  val bundleKey: String?
}

@Remote("java.awt.Point")
interface Point {
  val x: Int
  val y: Int
}