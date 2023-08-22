package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.Component
import org.intellij.lang.annotations.Language

fun Finder.editor(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='EditorComponentImpl']",
                                                                JEditorUiComponent::class.java)

class JEditorUiComponent(data: ComponentData) : UiComponent(data) {
  private val editor by lazy { driver.cast(component, EditorComponentImpl::class).getEditor() }
  private val document by lazy { editor.getDocument() }
  var text: String
    get() = document.getText()
    set(value) {
      driver.withWriteAction {
        document.setText(value)
      }
    }
}

@Remote("com.intellij.openapi.editor.impl.EditorComponentImpl")
interface EditorComponentImpl : Component {
  fun getEditor(): Editor
}

fun Finder.gutter(@Language("xpath") xpath: String = "//div[@class='EditorGutterComponentImpl']") = x(xpath, GutterUiComponent::class.java)

class GutterUiComponent(data: ComponentData) : UiComponent(data) {

}

@Remote("com.intellij.openapi.editor.impl.EditorGutterComponentImpl")
interface EditorGutterComponentImpl : Component {

}