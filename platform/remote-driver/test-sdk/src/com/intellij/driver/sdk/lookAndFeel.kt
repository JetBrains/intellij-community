package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.common.ideFrame

enum class IdeTheme(val searchName: String) {
  LIGHT("Light"),
  DARK("Dark"),
}

fun Driver.changeTheme(theme: IdeTheme) {
  invokeAction("ChangeLaf")
  ideFrame {
    keyboard {
      typeText(theme.searchName)
      enter()
    }
  }
}
