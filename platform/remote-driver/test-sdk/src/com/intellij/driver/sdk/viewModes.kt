package com.intellij.driver.sdk

import com.intellij.driver.client.Driver

fun Driver.toggleDistractionFreeMode() = invokeAction("ToggleDistractionFreeMode")

fun Driver.toggleZenMode() = invokeAction("ToggleZenMode")

fun Driver.togglePresentationMode() = invokeAction("TogglePresentationMode")

fun Driver.toggleFullscreenMode() = invokeAction("ToggleFullScreen")

fun Driver.toggleToolbarVisibility() = invokeAction("ViewToolBar")

fun Driver.removeMainToolbarActions() = invokeAction("RemoveMainToolbarActionsAction")