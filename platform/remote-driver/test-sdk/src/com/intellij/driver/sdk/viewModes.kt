package com.intellij.driver.sdk

import com.intellij.driver.client.Driver

fun Driver.toggleDistractionFreeMode() = invokeAction("ToggleDistractionFreeMode")

fun Driver.toggleZenMode() {
  invokeAction("ToggleZenMode")
  Thread.sleep(1500)
}

fun Driver.togglePresentationMode() {
  invokeAction("TogglePresentationMode")
  Thread.sleep(1500)
}

fun Driver.toggleFullscreenMode() {
  invokeAction("ToggleFullScreen")
  Thread.sleep(1500)
}

fun Driver.toggleToolbarVisibility() = invokeAction("ViewToolBar")

fun Driver.removeMainToolbarActions() = invokeAction("RemoveMainToolbarActionsAction")