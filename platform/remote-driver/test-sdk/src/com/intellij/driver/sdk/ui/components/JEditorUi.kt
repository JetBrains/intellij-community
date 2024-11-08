package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.DriverCallException
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.EditorComponentImplBeControlBuilder
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.center
import com.intellij.driver.sdk.ui.remote.Component
import org.intellij.lang.annotations.Language
import java.awt.Point

fun Finder.editor(@Language("xpath") xpath: String? = null): JEditorUiComponent {
  return x(xpath ?: "//div[@class='EditorComponentImpl']",
           JEditorUiComponent::class.java)
}

fun Finder.codeEditor(@Language("xpath") xpath: String? = null): JEditorUiComponent {
  return x(xpath ?: "//div[@class='EditorTabs']//div[@class='EditorComponentImpl']",
           JEditorUiComponent::class.java)
}

fun Finder.codeEditor(@Language("xpath") xpath: String? = null, action: JEditorUiComponent.() -> Unit) {
  x(xpath ?: "//div[@class='EditorTabs']//div[@class='EditorComponentImpl']",
    JEditorUiComponent::class.java).action()
}

fun Finder.editor(@Language("xpath") xpath: String? = null, action: JEditorUiComponent.() -> Unit) {
  x(xpath ?: "//div[@class='EditorComponentImpl']", JEditorUiComponent::class.java).action()
}

class JEditorUiComponent(data: ComponentData) : UiComponent(data) {
  private val editorComponent get() = driver.cast(component, EditorComponentImpl::class)
  private val document: Document by lazy { editor.getDocument() }
  private val caretPosition
    get() = editor.getCaretModel().getLogicalPosition()

  val editor: Editor by lazy { editorComponent.getEditor() }

  var text: String
    get() = document.getText()
    set(value) {
      driver.withWriteAction {
        document.setText(value)
      }
    }

  fun isEditable() = editorComponent.isEditable()

  fun isSoftWrappingEnabled() = interact { getSoftWrapModel().isSoftWrappingEnabled() }

  fun clickInlay(inlay: Inlay) {
    val inlayCenter = driver.withContext(OnDispatcher.EDT) { inlay.getBounds() }.center
    click(inlayCenter)
  }

  fun getInlayHints(): List<InlayHint> {
    val hints = mutableListOf<InlayHint>()
    this.editor.getInlayModel().getInlineElementsInRange(0, Int.MAX_VALUE).forEach { element ->
      val hintText = try {
        element.getRenderer().getText()
      }
      catch (e: DriverCallException) {
        try {
          driver.cast(element.getRenderer(), DeclarativeInlayRenderer::class).getPresentationList().getEntries().joinToString { it.getText() }
        }
        catch (e: DriverCallException) {
          element.getRenderer().toString().substring(1, element.getRenderer().toString().length - 1)
        }
      }
      hints.add(InlayHint(element.getOffset(), hintText!!))
    }
    return hints
  }

  fun setSelection(startOffset: Int, endOffset: Int) {
    interact {
      getSelectionModel().setSelection(startOffset, endOffset)
    }
  }


  fun deleteFile() {
    driver.withWriteAction {
      editor.getVirtualFile().delete(null)
    }
  }

  fun selectAndDrag(from: Point, to: Point, delayMs: Int) {
    robot.selectAndDrag(component, to, from, delayMs)
  }

  fun getCaretLine() = caretPosition.getLine() + 1
  fun getCaretColumn() = caretPosition.getColumn() + 1

  fun getFontSize() = editor.getColorsScheme().getEditorFontSize()

  fun clickOn(text: String, button: RemoteMouseButton) {
    val o = this.text.indexOf(text) + text.length / 2
    val point = interact {
      val p = offsetToVisualPosition(o)
      visualPositionToXY(p)
    }
    robot.click(component, point, button, 1)
  }

  fun setCaretPosition(line: Int, column: Int) {
    click()
    interact {
      getCaretModel().moveToLogicalPosition(driver.logicalPosition(line - 1, column - 1))
    }
  }

  fun moveCaretToOffset(offset: Int) {
    interact {
      getCaretModel().moveToOffset(offset)
    }
  }

  private fun calculatePositionPoint(line: Int, column: Int): Point {
    return interact {
      val lowerPoint = editor.logicalPositionToXY(driver.logicalPosition(line - 1, column - 1))
      Point(lowerPoint.getX().toInt(), lowerPoint.getY().toInt() + editor.getLineHeight() / 2)
    }
  }

  fun clickOnPosition(line: Int, column: Int) {
    setFocus()
    click(calculatePositionPoint(line, column))
  }

  fun hoverOnPosition(line: Int, column: Int) {
    setFocus()
    moveMouse(calculatePositionPoint(line, column))
  }

  fun getLineText(line: Int) = text.lines().getOrElse(line - 1) { "" }

  fun <T> interact(block: Editor.() -> T): T {
    return driver.withContext(OnDispatcher.EDT) {
      block.invoke(editor)
    }
  }

  fun invokeIntentionAction(intentionActionName: String) {
    driver.utility(IntentionActionUtils::class).invokeIntentionAction(editor, intentionActionName)
  }

  fun invokeQuickFix(highlightInfo: HighlightInfo, name: String) {
    driver.utility(IntentionActionUtils::class).invokeQuickFix(editor, highlightInfo, name)
  }

  fun invokeAiIntentionAction(intentionActionName: String) {
    driver.utility(AiTestIntentionUtils::class).invokeAiAssistantIntention(editor, intentionActionName)
  }
}

@Remote("com.jetbrains.performancePlugin.utils.IntentionActionUtils", plugin = "com.jetbrains.performancePlugin")
interface IntentionActionUtils {
  fun invokeIntentionAction(editor: Editor, intentionActionName: String)
  fun invokeQuickFix(editor: Editor, highlightInfo: HighlightInfo, name: String)
}

@Remote("com.intellij.ml.llm.intentions.TestIntentionUtils", plugin = "com.intellij.ml.llm/intellij.ml.llm.core")
interface AiTestIntentionUtils {
  fun invokeAiAssistantIntention(editor: Editor, intentionName: String)
}

@Remote("com.intellij.openapi.editor.impl.EditorComponentImpl")
@BeControlClass(EditorComponentImplBeControlBuilder::class)
interface EditorComponentImpl : Component {
  fun getEditor(): Editor
  fun isEditable(): Boolean
}

class EditorTextFieldUiComponent(data: ComponentData) : UiComponent(data) {
  val text: String by lazy { driver.cast(component, EditorTextField::class).getText() }
}

@Remote("com.intellij.ui.EditorTextField")
interface EditorTextField : Component {
  fun getText(): String
}

fun Finder.gutter(@Language("xpath") xpath: String = "//div[@class='EditorGutterComponentImpl']") = x(xpath, GutterUiComponent::class.java)

class GutterUiComponent(data: ComponentData) : UiComponent(data) {

  private val gutter by lazy { driver.cast(component, EditorGutterComponentImpl::class) }

  val icons: List<GutterIcon>
    get() = driver.withContext(OnDispatcher.EDT) {
      return@withContext gutter.getLineGutterMarks()
        .map { GutterIcon(it) }
    }

  val iconAreaOffset
    get() = gutter.getIconAreaOffset()


  fun getIconName(line: Int) =
    icons.firstOrNull { it.line == line - 1 }?.mark?.getIcon().toString().substringAfterLast("/")

  fun hoverOverIcon(line: Int) {
    moveMouse(icons.firstOrNull { it.line == line - 1 }!!.location)
  }

  fun rightClickOnIcon(line: Int) {
    rightClick(icons.firstOrNull { it.line == line - 1 }!!.location)
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

class InlayHint(val offset: Int, val text: String)

fun List<InlayHint>.getHint(offset: Int): InlayHint {
  val foundHint = this.find { it.offset.equals(offset) }
  if (foundHint == null) {
    throw NoSuchElementException("cannot find hint with offset: $offset")
  }
  return foundHint
}


@Remote("com.intellij.openapi.editor.impl.EditorGutterComponentImpl")
interface EditorGutterComponentImpl : Component {
  fun getLineGutterMarks(): List<GutterIconWithLocation>

  fun getIconAreaOffset(): Int
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
