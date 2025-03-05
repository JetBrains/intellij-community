package com.intellij.driver.sdk.ui.components.kubernetes

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import org.intellij.lang.annotations.Language

fun Finder.kubePopup(@Language("xpath") xpath: String? = null, action: KubePopupUI.() -> Unit) {
  x(xpath ?: "//div[@class='HeavyWeightWindow']", KubePopupUI::class.java).action()
}

class KubePopupUI(data: ComponentData): PopupUiComponent(data) {
  val deployResourceButton = x("//div[@defaulticon='deploy.svg']")
  val deleteResourceButton = x("//div[@defaulticon='undeploy.svg']")
  val namespacesList = x("//div[@class='MyList']", JListUiComponent::class.java)
}