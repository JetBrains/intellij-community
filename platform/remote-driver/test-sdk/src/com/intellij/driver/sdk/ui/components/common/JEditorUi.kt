package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.DriverCallException
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.EditorComponentImplBeControlBuilder
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.center
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.rdTarget
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.shouldContainText
import org.intellij.lang.annotations.Language
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import kotlin.time.Duration
import com.intellij.openapi.editor.markup.EffectType
import kotlin.time.Duration.Companion.milliseconds

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

fun Finder.codeEditorForFile(fileName: String): JEditorUiComponent = codeEditor("//div[@class='EditorTabs']//div[@accessiblename='Editor for $fileName']")

fun Finder.editor(@Language("xpath") xpath: String? = null, action: JEditorUiComponent.() -> Unit) {
  x(xpath ?: "//div[@class='EditorComponentImpl']", JEditorUiComponent::class.java).action()
}

open class JEditorUiComponent(data: ComponentData) : UiComponent(data) {
  private val caretPosition
    get() = editor.getCaretModel().getLogicalPosition()
  protected open val editorComponent: EditorComponentImpl
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

  fun getLineNumber(text: String): Int = document.getLineNumber(this.text.indexOf(text)) + 1

  fun getLastLineNumber(text: String): Int = document.getLineNumber(this.text.lastIndexOf(text)) + 1

  fun expandAllFoldings() {
    driver.invokeAction("ExpandAllRegions", component = component)
  }

  fun isEditable(): Boolean = editorComponent.isEditable()

  fun isSoftWrappingEnabled(): Boolean = interact { getSoftWrapModel().isSoftWrappingEnabled() }

  fun clickInlay(inlay: Inlay) {
    val inlayCenter = driver.withContext(OnDispatcher.EDT) { inlay.getBounds() }.center
    click(inlayCenter)
  }

  fun getInlayHints(braceAround: Boolean = true): List<InlayHint> {
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
          if (braceAround)
            element.getRenderer().toString().substring(1, element.getRenderer().toString().length - 1)
          else element.getRenderer().toString()
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

  fun getSelection(allCarets: Boolean = false): String? {
    return interact {
      getSelectionModel().getSelectedText(allCarets)
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

  fun getMultipleCaretPositions(): List<Pair<Int, Int>> {
    return editor.getCaretModel().getAllCarets().map { Pair(it.getLogicalPosition().getLine() + 1, it.getLogicalPosition().getColumn() + 1) }
  }

  fun getFontSize(): Int = editor.getColorsScheme().getEditorFontSize()

  fun clickOn(text: String, button: RemoteMouseButton = RemoteMouseButton.LEFT, times: Int = 1) {
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
      getCaretModel().moveToLogicalPosition(driver.logicalPosition(line - 1, column - 1, component.rdTarget))
    }
    scrollToCaret()
  }

  fun goToLine(line: Int): Unit = step("Go to $line line") {
    click()
    interact {
      getCaretModel().moveToLogicalPosition(driver.logicalPosition(line - 1, 1, component.rdTarget))
    }
    scrollToCaret()
  }

  fun moveCaretToOffset(offset: Int) {
    interact {
      getCaretModel().moveToOffset(offset)
    }
  }

  fun moveCaretToText(targetText: String) {
    interact {
      getCaretModel().moveToOffset(text.indexOf(targetText))
    }
  }

  private fun calculatePositionPoint(line: Int, column: Int): Point {
    return interact {
      val lowerPoint = editor.logicalPositionToXY(driver.logicalPosition(line - 1, column - 1))
      Point(lowerPoint.getX().toInt(), lowerPoint.getY().toInt() + editor.getLineHeight() / 2)
    }
  }

  fun clickOnPosition(line: Int, column: Int, scrollToPosition: Boolean = true) {
    if (scrollToPosition) {
      scrollToPosition(line, column)
    }
    click(calculatePositionPoint(line, column))
  }

  fun hoverOnPosition(line: Int, column: Int, scrollToPosition: Boolean = true) {
    if (scrollToPosition) {
      scrollToPosition(line, column)
    }
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

  /**
   * @see shouldContainText For better readability
   */
  @Deprecated("Use shouldContainText instead", ReplaceWith("shouldContainText(expectedText)"))
  fun containsText(expectedText: String) {
    step("Verify that editor contains text: $expectedText") {
      waitFor(errorMessage = { "Editor doesn't contain text: $expectedText" },
              getter = { text.trimIndent() },
              checker = { it.contains(expectedText) })
    }
  }

  /**
  * Retrieves inline completion text at the specified offset or current caret position.
  * @param offset The offset at which to retrieve inline completion. Defaults to the current caret position.
  * @return The inline completion text at the specified offset.
  */
  fun getInlineCompletion(offset: Int = interact { editor.getCaretModel().getOffset() }): String {
    val endOffset: Int = with(editor.getDocument()) {
      val lastLine = getLineCount() - 1
      getLineEndOffset(lastLine)
    }
    val inlineElements = editor.getInlayModel().getInlineElementsInRange(offset, endOffset).filter { it.getOffset() == offset }
    val blockElements = editor.getInlayModel().getBlockElementsInRange(offset, endOffset).filter { it.getOffset() == offset }

    val completions = (inlineElements + blockElements).mapNotNull { element ->
      try {
        driver.cast(element.getRenderer(), InlineCompletionLineRenderer::class).getBlocks().joinToString("") { it.text }
      }
      catch (_: DriverCallException) {
        return@mapNotNull null
      }
    }
    return completions.joinToString("\n")
  }

  fun getAfterLineHints(line: Int): List<String> = editor.getInlayModel().getAfterLineEndElementsForLogicalLine(line - 1)
    .mapNotNull {
      try {
        driver.cast(it.getRenderer(), HintRenderer::class).getText()
      }
      catch (_: DriverCallException) {
        return@mapNotNull null
      }
    }

  fun getAllHighlights(): List<HighlightInfo> = editor.getMarkupModel().getAllHighlighters().mapNotNull {
    driver.utility(HighlightInfo::class).fromRangeHighlighter(it)
  } + driver.getHighlights(editor.getDocument())

  fun getAllHighlightersTextAttributes(): List<TextAttributes> = editor.getMarkupModel().getAllHighlighters().mapNotNull {
    val attrs = it.getTextAttributes() ?: return@mapNotNull null
    TextAttributes(
      it.getStartOffset(),
      it.getEndOffset(),
      EffectType.valueOf(attrs.getEffectType().toString()),
      attrs.getEffectColor()?.run { Color(getRGB()) }
    )
  }

  fun scrollToPosition(line: Int, column: Int) {
    val position = driver.logicalPosition(line, column, component.rdTarget)
    val scrollType = scrollType()
    interact { editor.getScrollingModel().scrollTo(position, scrollType) }
    wait(200.milliseconds) // wait for scroll to finish
  }

  fun scrollToCaret() {
    val scrollType = scrollType()
    interact { editor.getScrollingModel().scrollToCaret(scrollType) }
    wait(200.milliseconds) // wait for scroll to finish
  }

  private fun scrollType(name: String = "CENTER") = driver.utility(ScrollType::class).valueOf(name)

  data class TextAttributes(val startOffset: Int, val endOffset: Int, val effectType: EffectType, val effectColor: Color?)
}

@Remote("com.jetbrains.performancePlugin.utils.IntentionActionUtils", plugin = "com.jetbrains.performancePlugin")
interface IntentionActionUtils {
  fun invokeIntentionAction(editor: Editor, intentionActionName: String)
  fun invokeQuickFix(editor: Editor, highlightInfo: HighlightInfo, name: String)
}

@Remote("com.intellij.ml.llm.codeGeneration.testGeneration.TestIntentionUtils", plugin = "com.intellij.ml.llm/intellij.ml.llm.core")
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

  fun icon(timeout: Duration = DEFAULT_FIND_TIMEOUT, errorMessage: String = "icon not found", predicate: (GutterIcon) -> Boolean): GutterIcon =
    waitFor(timeout = timeout, errorMessage = { errorMessage }, getter = { icons.filter(predicate) }, checker = { it.singleOrNull() != null }).single()

  fun getGutterIcons(): List<GutterIcon> {
    waitFor { this.icons.isNotEmpty() }
    return this.icons
  }

  fun getIconName(line: Int) =
    icons.firstOrNull { it.line == line - 1 }?.mark?.getIcon().toString().substringAfterLast("/")

  fun hoverOverIcon(line: Int) {
    moveMouse(icons.firstOrNull { it.line == line - 1 }!!.location)
  }

  fun clickOnIcon(line: Int) {
    click(icons.firstOrNull { it.line == line - 1 }!!.location)
  }

  fun rightClickOnIcon(line: Int) {
    rightClick(icons.firstOrNull { it.line == line - 1 }!!.location)
  }

  fun clickLineMarkerAtLine(lineNum: Int, accessibleName: String, lineY: Int? = null) {
    val lineIndex = lineNum - 1
    val rectangle = waitNotNull("No $accessibleName marker on line $lineNum") {
      driver.withContext(OnDispatcher.EDT) {
        gutter.getActiveGutterRendererRectangle(lineIndex, accessibleName)
      }
    }
    val lineY = lineY ?: driver.withContext(OnDispatcher.EDT) {
      val startY = gutter.getEditor().visualLineToY(lineIndex)
      startY + (gutter.getEditor().getLineHeight() / 2)
    }
    click(Point(rectangle.centerX.toInt(), lineY))
  }

  fun clickVcsLineMarkerAtLine(line: Int) {
    //to support a deleted block marker, click on the first third of the line
    val lineY = driver.withContext(OnDispatcher.EDT) {
      val startY = gutter.getEditor().visualLineToY(line - 1)
      startY + (gutter.getEditor().getLineHeight() / 6)
    }
    clickLineMarkerAtLine(line, "VCS marker: changed line", lineY)
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
  IMPLEMENT("expui/gutter/implementingMethod.svg"),
  CONSTEXPR_DEBUG("resharper/RunMarkers/DebugThis.svg")
}

data class GutterState(
  val lineNumber: Int,
  val iconPath: String = "",
)

data class InlayHint(val offset: Int, val text: String)

fun List<InlayHint>.getHint(offset: Int): InlayHint {
  val foundHint = this.find { it.offset == offset }
  if (foundHint == null) {
    throw NoSuchElementException("cannot find hint with offset: $offset")
  }
  return foundHint
}

fun Finder.editorSearchReplace(@Language("xpath") xpath: String? = null, action: EditorSearchReplaceComponent.() -> Unit) {
  x(xpath ?: "//div[@class='EditorCompositePanel']//div[@class='SearchReplaceComponent']",
    EditorSearchReplaceComponent::class.java).action()
}

class EditorSearchReplaceComponent(data: ComponentData) : UiComponent(data) {
  val searchField: JTextFieldUI = textField { and(byClass("JBTextArea"), byAccessibleName("Search")) }
  val replaceField: JTextFieldUI = textField { and(byClass("JBTextArea"), byAccessibleName("Replace")) }
  val clearSearchButton: ActionButtonUi = actionButton { byAttribute("myicon", "closeSmall.svg") }
  val newLineButton: ActionButtonUi = actionButton { byAccessibleName("New Line") }
  val matchCaseButton: ActionButtonUi = actionButton { byAccessibleName("Match Case") }
  val regexButton: ActionButtonUi = actionButton { byAccessibleName("Regex") }
  val preserveCaseButton: ActionButtonUi = actionButton { byAccessibleName("Preserve case") }
  val matchesLabel: UiComponent = x("//div[@class='ActionToolbarImpl']//div[@class='JLabel']")
  val nextOccurrenceButton: ActionButtonUi = actionButton { byAccessibleName("Next Occurrence") }
  val previousOccurrenceButton: ActionButtonUi = actionButton { byAccessibleName("Previous Occurrence") }
  val filterSearchResultsButton: ActionButtonUi = actionButton { byAccessibleName("Filter Search Results") }
  val optionsButton: ActionButtonUi = actionButton { byAccessibleName("Open in Window, Multiple Cursors") }
  val replaceButton: ActionButtonUi = actionButton { byVisibleText("Replace") }
  val replaceAllButton: ActionButtonUi = actionButton { byAccessibleName("Replace All") }
  val excludeButton: ActionButtonUi = actionButton { byAccessibleName("Exclude") }
  val closeSearchReplaceButton: ActionButtonUi = actionButton { byAccessibleName("Close") }
  val searchHistoryButton: ActionButtonUi = actionButton { byAccessibleName("Search History") }

  // The components below are available in "find in large file" only
  val matchCaseCheckBox: JCheckBoxUi = checkBox { byAccessibleName("Match сase") }
  val wordsCheckBox: JCheckBoxUi = checkBox { byAccessibleName("Words") }
  val regexCheckBox: JCheckBoxUi = checkBox { byAccessibleName("Regex") }
  val searchAllButton: ActionButtonUi = actionButton { byAccessibleName("Search All") }
  val searchBackwardButton: ActionButtonUi = actionButton { byAccessibleName("Search Backward") }
  val searchForwardButton: ActionButtonUi = actionButton { byAccessibleName("Search Forward") }
}

@Remote("com.intellij.openapi.editor.impl.EditorGutterComponentImpl")
interface EditorGutterComponentImpl : Component {
  fun getLineGutterMarks(): List<GutterIconWithLocation>

  fun getIconAreaOffset(): Int

  fun getActiveGutterRendererRectangle(lineNum: Int, accessibleName: String): Rectangle?

  fun getEditor(): Editor
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
