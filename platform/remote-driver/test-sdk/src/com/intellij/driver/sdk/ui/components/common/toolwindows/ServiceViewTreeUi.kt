package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import org.intellij.lang.annotations.Language

fun Finder.servicesTree(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='ServiceViewTree']", ServiceViewTreeUi::class.java)

class ServiceViewTreeUi(data: ComponentData) : JTreeUiComponent(data) {
  private fun findInServicesTree(vararg path: String, fullMatch: Boolean): TreePathToRow? =
    super.findExpandedPath(*path, fullMatch = fullMatch)

  fun isRootExist(root: String):Boolean = (findInServicesTree(root, fullMatch = true) != null)
}