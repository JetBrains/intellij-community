package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

fun Finder.djangoStructureToolWindow(@Language("xpath") xpath: String? = null) =
  x(xpath
    ?: "//div[@class='DjangoStructureView']", DjangoStructureToolWindowUiComponent::class.java)

class DjangoStructureToolWindowUiComponent(data: ComponentData) : UiComponent(data) {
  val tree
    get() = x("//div[@class='Tree']", JTreeUiComponent::class.java)
}