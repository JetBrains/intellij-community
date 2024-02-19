package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.dialog(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyDialog']", UiComponent::class.java)