package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.list

fun Finder.intentionPreview(): GoIntentionPreviewUi =
  x(GoIntentionPreviewUi::class.java) { byClass("IntentionPreviewComponent") }

fun Finder.inspectionOptionList(option: String): JListUiComponent =
  list { byClass("MyList") and contains(byVisibleText(option)) }

class GoIntentionPreviewUi(data: ComponentData) : UiComponent(data) {
  val editors = xx(JEditorUiComponent::class.java) { byClass("EditorComponentImpl") }
}

