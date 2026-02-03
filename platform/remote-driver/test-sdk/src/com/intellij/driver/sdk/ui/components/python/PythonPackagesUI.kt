package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import org.intellij.lang.annotations.Language

fun Finder.pythonPackagesToolWindow(@Language("xpath") xpath: String? = null) =
  x(xpath
    ?: "//div[@class='InternalDecoratorImpl'][.//div[@class='PyPackagingToolWindowPanel']]", PythonPackagesToolWindowUiComponent::class.java)

class PythonPackagesToolWindowUiComponent(data: ComponentData) : UiComponent(data) {
  val packagesTable
    get() = x("//div[@class='JPanel'][div[@class='JPanel'][div[@class='JBLabel' and contains(@accessiblename, 'Installed')]]]", UiComponent::class.java)

  val searchField
    get() = x("//div[@class='PyPackageSearchTextField']", UiComponent::class.java)

  // Gear button in the Python Packages tool window toolbar
  val settingsButton
    get() = x("//div[@myicon='settings.svg']", UiComponent::class.java)

  val installButton = x { and((byClass("JBOptionButton")), (byAccessibleName("Install"))) }

  val uninstallButton = x { byAccessibleName("Uninstall") }
}