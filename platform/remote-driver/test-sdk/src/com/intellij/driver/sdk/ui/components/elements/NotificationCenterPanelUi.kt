package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.notificationPanel() = x("//div[@class='NotificationCenterPanel']", UiComponent::class.java)
