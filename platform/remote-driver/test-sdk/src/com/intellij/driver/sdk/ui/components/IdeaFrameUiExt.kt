package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Locators

fun IdeaFrameUI.buildToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = x(Locators.byType("com.intellij.build.BuildView")).apply(action)

fun IdeaFrameUI.notificationsToolWindow(action: UiComponent.() -> Unit = {}): UiComponent = x(Locators.byAccessibleName("Notifications Tool Window")).apply(action)