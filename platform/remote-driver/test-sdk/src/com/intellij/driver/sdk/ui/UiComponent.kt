package com.intellij.driver.sdk.ui

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.model.TextData
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.*
import java.awt.Point


@Suppress("MemberVisibilityCanBePrivate")
open class UiComponent(private val remoteComponent: RemoteComponent) : WithKeyboard, Finder {
  override val searchContext: SearchContext = remoteComponent

  // Search Text Locations
  fun findText(text: String): UiText {
    return findAllText {
      it.text == text
    }.single()
  }

  fun findText(predicate: (TextData) -> Boolean): UiText {
    return remoteComponent.findAllText().single(predicate).let { UiText(this, it) }
  }

  fun findAllText(predicate: (TextData) -> Boolean): List<UiText> {
    return remoteComponent.findAllText().filter(predicate).map { UiText(this, it) }
  }

  fun findAllText(): List<UiText> {
    return remoteComponent.findAllText().map { UiText(this, it) }
  }

  // Mouse
  fun click(point: Point? = null) {
    with(remoteComponent) {
      if (point != null) {
        robot.click(component, point)
      } else {
        robot.click(component)
      }
    }
  }

  fun doubleClick(point: Point? = null) {
    with(remoteComponent) {
      if (point != null) {
        robot.click(component, point, RemoteMouseButton.LEFT, 2)
      } else {
        robot.doubleClick(component)
      }
    }
  }

  fun rightClick(point: Point? = null) {
    with(remoteComponent) {
      if (point != null) {
        robot.click(component, point, RemoteMouseButton.RIGHT, 1)
      } else {
        robot.rightClick(component)
      }
    }
  }
  fun click(button: RemoteMouseButton, count: Int) {
    with(remoteComponent) {
      robot.click(component, button, count)
    }
  }

  fun moveMouse() {
    with(remoteComponent) {
      robot.moveMouse(component)
    }
  }
  fun moveMouse(point: Point) {
    with(remoteComponent) {
      robot.moveMouse(component, point)
    }
  }
}