package com.intellij.driver.sdk.ui.components.aqua

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.jcef
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.waitFor


fun Finder.webInspector(action: WebInspectorUI.() -> Unit) {
  x("//div[@accessiblename='Web Inspector Tool Window']", WebInspectorUI::class.java).action()
}

class WebInspectorUI(data: ComponentData) : UiComponent(data) {
  private val url = textField("//div[@myhistorypropertyname='PageObjectEditor.URLHistory']//div[@class='TextFieldWithProcessing']")
  val htmlSource = x("//div[@class='PageStructureViewComponent']")

  fun setUrl(url: String) {
    this.url.text = url
    waitFor { this.url.text == url }
    this.url.click()
    keyboard { enter() }
  }

  fun selectElement(selector: String) {
    locatorEvaluator { selectElement.click() }
    jcef {
      findElement(selector).scroll()
      findElement(selector).clickAtCenter()
    }
  }
}
