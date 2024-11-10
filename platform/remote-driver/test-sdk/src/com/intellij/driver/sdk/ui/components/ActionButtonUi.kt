package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.accessibleName
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Finder.actionButton(text: String) =
  x("//div[@class='ActionButton' and @visible_text='$text']", ActionButtonUi::class.java)

fun Finder.actionButton(init: QueryBuilder.() -> String) = x(ActionButtonUi::class.java, init)

fun Finder.actionButtonByXpath(xpath: String) =
  x(xpath, ActionButtonUi::class.java)

class ActionButtonUi(data: ComponentData): UiComponent(data) {
  val actionButtonComponent: ActionButtonComponent get() = driver.cast(component, ActionButtonComponent::class)
  val icon: String get() = actionButtonComponent.getIcon().toString()
  val text: String get() = actionButtonComponent.getPresentation().getText()
  val isSelected: Boolean get() = actionButtonComponent.isSelected()
}

fun ActionButtonUi.waitSelected(selected: Boolean, timeout: Duration = 5.seconds) {
  waitFound()
  waitFor("'${accessibleName}' action button is ${if (selected) "selected" else "not selected"}", timeout) {
    isSelected == selected
  }
}

@Remote("com.intellij.openapi.actionSystem.impl.ActionButton")
interface ActionButtonComponent {
  fun getIcon(): Icon
  fun getPresentation(): PresentationRef
  fun isSelected(): Boolean
}

@Remote("com.intellij.openapi.actionSystem.Presentation")
interface PresentationRef {
  fun getText(): String
}