package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.components.elements.balloon

/** The link of the [notificationTitle] notification which installs a missing Go tool. */
internal fun Finder.installGoToolLink(notificationTitle: String, linkTextFragment: String): UiText =
  balloon(notificationTitle).waitAnyTextsContains(linkTextFragment).first()
