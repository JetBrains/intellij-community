package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.documentationHintEditorPane(block: DocumentationHintEditorPaneUi.() -> Unit = {}) =
  x(DocumentationHintEditorPaneUi::class.java) {
    byClass("DocumentationHintEditorPane")
  }.apply(block)

class DocumentationHintEditorPaneUi(data: ComponentData) : UiComponent(data){
  private val documentationHintEditorPaneComponent by lazy { driver.cast(component, DocumentationHintEditorPane::class)}

  fun getText(): String = documentationHintEditorPaneComponent.getText()
}

@Remote("com.intellij.codeInsight.documentation.DocumentationHintEditorPane")
interface DocumentationHintEditorPane {
  fun getText(): String
}