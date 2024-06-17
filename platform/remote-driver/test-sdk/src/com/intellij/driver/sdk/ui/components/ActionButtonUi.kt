package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote

class ActionButtonUi(data: ComponentData): UiComponent(data) {
  val actionButtonComponent: ActionButtonComponent get() = driver.cast(component, ActionButtonComponent::class)
  val icon: String get() = actionButtonComponent.getIcon().toString()
  val text: String get() = actionButtonComponent.getPresentation().getText()
}

@Remote("com.intellij.openapi.actionSystem.impl.ActionButton")
interface ActionButtonComponent {
  fun getIcon(): Icon
  fun getPresentation(): PresentationRef
}

@Remote("com.intellij.openapi.actionSystem.Presentation")
interface PresentationRef {
  fun getText(): String
}