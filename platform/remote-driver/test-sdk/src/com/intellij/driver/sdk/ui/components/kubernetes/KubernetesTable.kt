package com.intellij.driver.sdk.ui.components.kubernetes

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import org.intellij.lang.annotations.Language

fun Finder.kubernetesTable(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='KubernetesTable']", KubernetesTableUi::class.java)

class KubernetesTableUi(data: ComponentData) : JTableUiComponent(data)
