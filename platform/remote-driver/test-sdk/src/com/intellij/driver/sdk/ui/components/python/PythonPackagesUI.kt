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
    get() = x("//div[@class='JBScrollPane'][.//div[@class='PyPackagesTree']]", UiComponent::class.java, "Python packages list")

  val searchField
    get() = x("//div[@class='PyPackageSearchTextField']", UiComponent::class.java, "package search field")

  val optionsButton
    get() = x("'Options' tool window button") { and(byClass("ActionButton"), byAccessibleName("Options")) }

  val installButton = x { and((byClass("JBOptionButton")), (byAccessibleName("Install"))) }

  val uninstallButton = x { byAccessibleName("Uninstall") }
}