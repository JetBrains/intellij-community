package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.RemoteComponent
import com.intellij.driver.sdk.ui.remote.SearchContext
import com.intellij.driver.sdk.ui.remote.TextData

@Suppress("MemberVisibilityCanBePrivate")
open class UiComponent(private val remoteComponent: RemoteComponent) : WithKeyboard, ComponentFinder {
  override val searchContext: SearchContext = remoteComponent

  // Search Text Locations
  fun findText(text: String): TextData {
    return findAllText {
      it.text == text
    }.single()
  }

  fun findText(predicate: (TextData) -> Boolean): TextData {
    return findAllText().single(predicate)
  }

  fun findAllText(predicate: (TextData) -> Boolean): List<TextData> {
    return findAllText().filter(predicate)
  }

  fun findAllText(): List<TextData> {
    return remoteComponent.findAllText()
  }

  // Mouse
  fun click() {
    with(remoteComponent) {
      robot.click(component)
    }
  }

  fun doubleClick() {
    with(remoteComponent) {
      robot.doubleClick(component)
    }
  }

  fun rightClick() {
    with(remoteComponent) {
      robot.rightClick(component)
    }
  }
}