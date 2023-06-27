package com.intellij.driver.sdk.ui

import com.intellij.driver.sdk.ui.remote.RemoteComponent
import com.intellij.driver.sdk.ui.remote.TextData
import org.intellij.lang.annotations.Language
import java.time.Duration


open class UiComponent(private val remoteComponent: RemoteComponent) {

  // Searching
  fun find(@Language("xpath") xpath: String, timeout: Duration = Duration.ofSeconds(5)): UiComponent {
    return find(xpath, UiComponent::class.java, timeout)
  }

  fun findAll(@Language("xpath") xpath: String): List<UiComponent> {
    return findAll(xpath, UiComponent::class.java)
  }

  // PageObject Support
  fun <T : UiComponent> find(@Language("xpath") xpath: String, uiType: Class<T>, timeout: Duration = Duration.ofSeconds(5)): T {
    waitFor(timeout, errorMessage = "Can't find uiComponent with '$xpath' inside '${remoteComponent.foundByXpath}'") {
      findAll(xpath, uiType).size == 1
    }
    return findAll(xpath, uiType).first()
  }

  fun <T : UiComponent> findAll(@Language("xpath") xpath: String, uiType: Class<T>): List<T> {
    return remoteComponent.findAll(xpath).map {
      uiType.getConstructor(RemoteComponent::class.java)
        .newInstance(it)
    }
  }

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