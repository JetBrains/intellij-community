package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.DriverCallException
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RdTarget
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.DeclarativeInlayRenderer
import com.intellij.driver.sdk.Document
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.HighlightInfo
import com.intellij.driver.sdk.Inlay
import com.intellij.driver.sdk.logicalPosition
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.EditorComponentImplBeControlBuilder
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.center
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.waitFor
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

open class JEditorUiComponent(data: ComponentData) : UiComponent(data) {
  private val caretPosition
    get() = editor.getCaretModel().getLogicalPosition()
  protected open val editorComponent : EditorComponentImpl
    get() = driver.cast(component, EditorComponentImpl::class)

  val editor: Editor get() = editorComponent.getEditor()
  val document: Document get() = editor.getDocument()

  var text: String
    get() = document.getText()
    set(value) {
      step("Set text '$value'") {
        driver.withWriteAction {
          document.setText(value)
        }
      }
    }

  fun isEditable(): Boolean = editorComponent.isEditable()

  fun isSoftWrappingEnabled(): Boolean = interact { getSoftWrapModel().isSoftWrappingEnabled() }

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

  fun getSelection(): String? {
    return interact {
      getSelectionModel().getSelectedText()
    }
  }

  fun removeSelection() {
    return interact {
      getSelectionModel().removeSelection()
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

  fun getCaretLine(): Int = caretPosition.getLine() + 1
  fun getCaretColumn(): Int = caretPosition.getColumn() + 1

  fun getFontSize(): Int = editor.getColorsScheme().getEditorFontSize()

  fun clickOn(text: String, button: RemoteMouseButton, times: Int = 1) {
    val offset = this.text.indexOf(text) + text.length / 2
    val point = interact {
      val p = offsetToVisualPosition(offset)
      visualPositionToXY(p)
    }
    robot.click(component, point, button, times)
  }

  fun goToPosition(line: Int, column: Int): Unit = step("Go to position $line line $column column") {
    click()
    interact {
      getCaretModel().moveToLogicalPosition(driver.logicalPosition(line - 1, column - 1, (this as? RefWrapper)?.getRef()?.rdTarget ?: RdTarget.DEFAULT))
    }
  }

  fun goToLine(line: Int): Unit = step("Go to $line line") {
    click()
    interact {
      getCaretModel().moveToLogicalPosition(driver.logicalPosition(line - 1, 1, (this as? RefWrapper)?.getRef()?.rdTarget ?: RdTarget.DEFAULT))
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

  fun getLineText(line: Int): String = text.lines().getOrElse(line - 1) { "" }

  fun <T> interact(block: Editor.() -> T): T {
    return driver.withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
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

  fun containsText(expectedText: String) {
    step("Verify that editor contains text: $expectedText") {
      waitFor(errorMessage = { "Editor doesn't contain text: $expectedText" },
              getter = { text.trimIndent() },
              checker = { it.contains(expectedText) })
    }
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

fun Finder.gutter(@Language("xpath") xpath: String = "//div[@class='EditorGutterComponentImpl']"): GutterUiComponent = x(xpath, GutterUiComponent::class.java)

class GutterUiComponent(data: ComponentData) : UiComponent(data) {

  private val gutter by lazy { driver.cast(component, EditorGutterComponentImpl::class) }

  val icons: List<GutterIcon>
    get() = driver.withContext(OnDispatcher.EDT) {
      return@withContext gutter.getLineGutterMarks()
        .map { GutterIcon(it) }
    }

  val iconAreaOffset
    get() = gutter.getIconAreaOffset()


  fun getGutterIcons(): List<GutterIcon> {
    waitFor { this.icons.isNotEmpty() }
    return this.icons
  }

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

    fun getIconPath(): String {
      return mark
        .getIcon()
        .toString()
        .split(',', '(', ')')
        .findLast { it.trim().startsWith("path") }!!.split('=')[1]
    }
  }
}

enum class GutterIcon(val path: String) {
  RUN("expui/gutter/run.svg"),
  RUNSUCCESS("expui/gutter/runSuccess.svg"),
  RUNERROR("expui/gutter/runError.svg"),
  RERUN("expui/gutter/rerun.svg"),
  BREAKPOINT("expui/breakpoints/breakpoint.svg"),
  BREAKPOINT_VALID("expui/breakpoints/breakpointValid.svg"),
  NEXT_STATEMENT("expui/debugger/nextStatement.svg"),
  GOTO("icons/expui/assocFile@14x14.svg"),
  IMPLEMENT("expui/gutter/implementingMethod.svg")
}

data class GutterState(
  val lineNumber: Int,
  val iconPath: String = "",
)


class InlayHint(val offset: Int, val text: String)

fun List<InlayHint>.getHint(offset: Int): InlayHint {
  val foundHint = this.find { it.offset == offset }
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
