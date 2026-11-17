package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.ideFrame

enum class IdeTheme(val searchName: String) {
  LIGHT("Light"),
  DARK("Dark"),
}

/**
 * Selects [theme] in the theme popup. The new UI can hide the legacy Light and Dark themes, so the function shows them first.
 */
fun Driver.changeTheme(theme: IdeTheme) {
  setRegistry("ide.show.legacy.themes", true)
  invokeAction("ChangeLaf")
  ideFrame {
    keyboard {
      typeText(theme.searchName)
      enter()
    }
  }
}
