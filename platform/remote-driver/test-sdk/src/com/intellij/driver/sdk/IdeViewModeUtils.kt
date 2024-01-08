package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.menuAndToolbarsSettings
import com.intellij.driver.sdk.ui.components.searchEverywherePopup

fun Driver.toggleDistractionFreeMode() {
  invokeAction("ToggleDistractionFreeMode")
  Thread.sleep(1000)
}

fun Driver.toggleZenMode() {
  invokeAction("ToggleZenMode")
  Thread.sleep(5000)
}

fun Driver.togglePresentationMode() {
  invokeAction("TogglePresentationMode")
  Thread.sleep(5000)
}

fun Driver.toggleFullscreenMode() {
  invokeAction("ToggleFullScreen")
  Thread.sleep(5000)
}

fun Driver.toggleToolbarVisibility() {
  invokeAction("ViewToolBar")
  Thread.sleep(1000)
}

fun Driver.removeMainToolbarActions() {
  invokeAction("RemoveMainToolbarActionsAction")
}

fun Driver.openMenuAndToolbarsSettings(ui: UiRobot) {
  invokeAction("SearchEverywhere")

  ui.ideFrame {
    searchEverywherePopup().apply {
      searchAndChooseFirst("Customize Menus and Toolbars...")
    }
  }
}

fun Driver.restoreMainToolbarActions(ui: UiRobot) {
  openMenuAndToolbarsSettings(ui)
  ui.menuAndToolbarsSettings {
    restoreActionsButton.click()
    keyboard { enter() }
    okButton.click()
  }
  Thread.sleep(1000)
}

fun Driver.toggleMainMenuInSeparateToolbar(ui: UiRobot) {
  invokeAction("SearchEverywhere")

  ui.ideFrame {
    searchEverywherePopup().apply {
      searchAndChooseFirst("UI: Show main menu in a separate toolbar")
    }
    keyboard { escape() }
  }
}