package com.intellij.driver.sdk.ui.components.go

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery

fun Finder.goWelcomeScreen(action: GoWelcomeScreenUI.() -> Unit = {}): GoWelcomeScreenUI {
  return x(xQuery { byTitle("GoLandWorkspace – Welcome to GoLand") }, GoWelcomeScreenUI::class.java).apply(action)
}

fun Driver.goWelcomeScreen(action: GoWelcomeScreenUI.() -> Unit = {}): GoWelcomeScreenUI {
  return this.ui.goWelcomeScreen(action)
}

class GoWelcomeScreenUI(data: ComponentData) : WelcomeScreenUI(data) {
  val newButton: UiComponent
    get() = x(xQuery { byVisibleText("New…") })

  val cloneButton: UiComponent
    get() = x(xQuery { byAccessibleName("Clone Repository…") })
}
