package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

fun Finder.pythonPackagesToolWindow(@Language("xpath") xpath: String? = null) =
  x(xpath
    ?: "//div[@class='InternalDecoratorImpl'][.//div[@class='PyPackagingToolWindowPanel']]", PythonPackagesToolWindowUiComponent::class.java)

class PythonPackagesToolWindowUiComponent(data: ComponentData) : UiComponent(data) {
  val packagesTable
    get() = x("//div[@class='PyPackagesTable']", JTableUiComponent::class.java)
}