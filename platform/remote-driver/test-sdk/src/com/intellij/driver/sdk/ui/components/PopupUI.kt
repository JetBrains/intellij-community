package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.popup(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='HeavyWeightWindow']",
                                                               PopupUiComponent::class.java)

class PopupUiComponent(data: ComponentData) : UiComponent(data)