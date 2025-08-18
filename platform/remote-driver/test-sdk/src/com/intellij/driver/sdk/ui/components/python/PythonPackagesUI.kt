package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import org.intellij.lang.annotations.Language

fun Finder.pythonPackagesToolWindow(@Language("xpath") xpath: String? = null) =
  x(xpath
    ?: "//div[@class='InternalDecoratorImpl'][.//div[@class='PyPackagingToolWindowPanel']]", PythonPackagesToolWindowUiComponent::class.java)

class PythonPackagesToolWindowUiComponent(data: ComponentData) : UiComponent(data) {
  val packagesTable
    get() = x("//div[@class='PyPackagesTable']", JTableUiComponent::class.java)

  val searchField
    get() = x("//div[@class='TextFieldWithProcessing']", UiComponent::class.java)

  val installButton
    get() = x("//div[@class='JBOptionButton']", UiComponent::class.java)

  val uninstallButton
    get() = x("//div[@accessiblename='Uninstall']", UiComponent::class.java)
}