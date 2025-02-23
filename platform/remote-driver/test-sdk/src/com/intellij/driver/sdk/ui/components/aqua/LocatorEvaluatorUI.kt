package com.intellij.driver.sdk.ui.components.aqua

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.list


fun Finder.locatorEvaluator(action: LocatorEvaluatorUI.() -> Unit) {
  x("//*[contains(@visible_text,'Locators Evaluator')]/../../..", LocatorEvaluatorUI::class.java).action()
}


class LocatorEvaluatorUI(data: ComponentData) : UiComponent(data) {
  val selectElement = x("//div[contains(@myaction, 'Select Element')]")
  val mainFrame = x("//div[@myicon='web.svg']")
}

fun Finder.iframes() = list("//div[@class='ContextJBList']")
