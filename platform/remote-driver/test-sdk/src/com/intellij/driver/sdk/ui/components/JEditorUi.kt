package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Document
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.Component
import org.intellij.lang.annotations.Language
import java.awt.Point

fun Finder.editor(@Language("xpath") xpath: String? = null): JEditorUiComponent {
  return x(xpath ?: "//div[@class='EditorComponentImpl']",
           JEditorUiComponent::class.java)
}

class JEditorUiComponent(data: ComponentData) : UiComponent(data) {
  val editor: Editor by lazy { driver.cast(component, EditorComponentImpl::class).getEditor() }

  private val document: Document by lazy { editor.getDocument() }

  var text: String
    get() = document.getText()
    set(value) {
      driver.withWriteAction {
        document.setText(value)
      }
    }

  fun interact(block: Editor.() -> Unit) {
    driver.withContext(OnDispatcher.EDT) {
      block.invoke(editor)
    }
  }
}

@Remote("com.intellij.openapi.editor.impl.EditorComponentImpl")
interface EditorComponentImpl : Component {
  fun getEditor(): Editor
}

fun Finder.gutter(@Language("xpath") xpath: String = "//div[@class='EditorGutterComponentImpl']") = x(xpath, GutterUiComponent::class.java)

class GutterUiComponent(data: ComponentData) : UiComponent(data) {
  private val gutter by lazy { driver.cast(component, EditorGutterComponentImpl::class) }
  val icons: List<GutterIcon>
    get() = driver.withContext(OnDispatcher.EDT) {
      return@withContext gutter.getLineGutterMarks()
        .map { GutterIcon(it) }
    }


  inner class GutterIcon(private val data: GutterIconWithLocation) {
    val line: Int
      get() = data.getLine()
    val mark: GutterMark
      get() = data.getMark()
    val location: Point
      get() = data.getLocation()

    fun click() {
      click(location)
    }
  }
}

@Remote("com.intellij.openapi.editor.impl.EditorGutterComponentImpl")
interface EditorGutterComponentImpl : Component {
  fun getLineGutterMarks(): List<GutterIconWithLocation>
}

@Remote("com.intellij.openapi.editor.impl.GutterIconWithLocation")
interface GutterIconWithLocation {
  fun getLine(): Int
  fun getMark(): GutterMark
  fun getLocation(): Point
}

@Remote("com.intellij.codeInsight.daemon.GutterMark")
interface GutterMark {
  fun getTooltipText(): String?
  fun getIcon(): Icon
}

@Remote("javax.swing.Icon")
interface Icon