// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import training.learn.lesson.LessonManager
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JTextPane
import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class LessonMessagePane(private val panelMode: Boolean = true) : JTextPane() {
  enum class MessageState { NORMAL, PASSED, INACTIVE, RESTORE, INFORMER }

  private data class LessonMessage(
    val messageParts: List<MessagePart>,
    var state: MessageState,
    var start: Int = 0,
    var end: Int = 0
  )

  private data class RangeData(var range: IntRange, val action: (Point, Int) -> Unit)

  private val activeMessages = mutableListOf<LessonMessage>()
  private val restoreMessages = mutableListOf<LessonMessage>()
  private val inactiveMessages = mutableListOf<LessonMessage>()

  private val fontFamily: String = UIUtil.getLabelFont().fontName

  private val ranges = mutableSetOf<RangeData>()

  private var insertOffset: Int = 0

  private var paragraphStyle = SimpleAttributeSet()

  private fun allLessonMessages() = activeMessages + restoreMessages + inactiveMessages

  var currentAnimation = 0
  var totalAnimation = 0

  //, fontFace, check_width + check_right_indent
  init {
    UIUtil.doNotScrollToCaret(this)
    initStyleConstants()
    isEditable = false
    val listener = object : MouseAdapter() {
      override fun mouseClicked(me: MouseEvent) {
        val rangeData = getRangeDataForMouse(me) ?: return
        val middle = (rangeData.range.first + rangeData.range.last) / 2
        val rectangle = modelToView(middle)
        rangeData.action(Point(rectangle.x, (rectangle.y + rectangle.height/2)), rectangle.height)
      }

      override fun mouseMoved(me: MouseEvent) {
        val rangeData = getRangeDataForMouse(me)
        cursor = if (rangeData == null) {
          Cursor.getDefaultCursor()
        }
        else {
          Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
      }
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)
  }

  private fun getRangeDataForMouse(me: MouseEvent): RangeData? {
    val point = Point(me.x, me.y)
    val offset = viewToModel(point)
    val result = ranges.find { offset in it.range } ?: return null
    if (offset < 0 || offset >= document.length) return null
    for (i in result.range) {
      val rectangle = modelToView(i)
      if (me.x >= rectangle.x && me.y >= rectangle.y && me.y <= rectangle.y + rectangle.height) {
        return result
      }
    }
    return null
  }

  private fun initStyleConstants() {
    val fontSize = UISettings.instance.fontSize.toInt()

    StyleConstants.setForeground(INACTIVE, UISettings.instance.passedColor)

    StyleConstants.setFontFamily(REGULAR, fontFamily)
    StyleConstants.setFontSize(REGULAR, fontSize)
    StyleConstants.setForeground(REGULAR, JBColor.BLACK)

    StyleConstants.setFontFamily(BOLD, fontFamily)
    StyleConstants.setFontSize(BOLD, fontSize)
    StyleConstants.setBold(BOLD, true)
    StyleConstants.setForeground(BOLD, JBColor.BLACK)

    StyleConstants.setFontFamily(SHORTCUT, fontFamily)
    StyleConstants.setFontSize(SHORTCUT, fontSize)
    StyleConstants.setBold(SHORTCUT, true)
    StyleConstants.setForeground(SHORTCUT, JBColor.BLACK)

    StyleConstants.setForeground(CODE, UISettings.instance.codeForegroundColor)
    EditorColorsManager.getInstance().globalScheme.editorFontName
    StyleConstants.setFontFamily(CODE, EditorColorsManager.getInstance().globalScheme.editorFontName)
    StyleConstants.setFontSize(CODE, fontSize)

    StyleConstants.setForeground(LINK, JBColor.BLUE)
    StyleConstants.setFontFamily(LINK, fontFamily)
    StyleConstants.setUnderline(LINK, true)
    StyleConstants.setFontSize(LINK, fontSize)

    StyleConstants.setLeftIndent(TASK_PARAGRAPH_STYLE, UISettings.instance.checkIndent.toFloat())
    StyleConstants.setRightIndent(TASK_PARAGRAPH_STYLE, 0f)
    StyleConstants.setSpaceAbove(TASK_PARAGRAPH_STYLE, 24.0f)
    StyleConstants.setSpaceBelow(TASK_PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setLineSpacing(TASK_PARAGRAPH_STYLE, 0.2f)

    StyleConstants.setLeftIndent(INTERNAL_PARAGRAPH_STYLE, UISettings.instance.checkIndent.toFloat())
    StyleConstants.setRightIndent(INTERNAL_PARAGRAPH_STYLE, 0f)
    StyleConstants.setSpaceAbove(INTERNAL_PARAGRAPH_STYLE, 8.0f)
    StyleConstants.setSpaceBelow(INTERNAL_PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setLineSpacing(INTERNAL_PARAGRAPH_STYLE, 0.2f)

    StyleConstants.setLineSpacing(BALLOON_STYLE, 0.2f)

    StyleConstants.setForeground(REGULAR, UISettings.instance.defaultTextColor)
    StyleConstants.setForeground(BOLD, UISettings.instance.defaultTextColor)
    StyleConstants.setForeground(SHORTCUT, UISettings.instance.shortcutTextColor)
    StyleConstants.setForeground(LINK, UISettings.instance.lessonLinkColor)
    StyleConstants.setForeground(CODE, UISettings.instance.codeForegroundColor)

  }

  fun messagesNumber(): Int = activeMessages.size

  private fun removeMessagesRange(startIdx: Int, endIdx: Int, list: MutableList<LessonMessage>) {
    if (startIdx == endIdx) return
    list.subList(startIdx, endIdx).clear()
  }

  fun clearRestoreMessages(): () -> Rectangle? {
    removeMessagesRange(0, restoreMessages.size, restoreMessages)
    redrawMessages()
    val lastOrNull = activeMessages.lastOrNull()
    return { lastOrNull?.let { getRectangleToScroll(it) } }
  }

  fun removeInactiveMessages(number: Int) {
    removeMessagesRange(0, number, inactiveMessages)
    redrawMessages()
  }

  fun resetMessagesNumber(number: Int): () -> Rectangle? {
    val move = activeMessages.subList(number, activeMessages.size)
    move.forEach {
      it.state = MessageState.INACTIVE
    }
    inactiveMessages.addAll(0, move)
    move.clear()
    return clearRestoreMessages()
  }

  private fun insertText(text: String, attributeSet: AttributeSet) {
    document.insertString(insertOffset, text, attributeSet)
    styledDocument.setParagraphAttributes(insertOffset, text.length - 1, paragraphStyle, true)
    insertOffset += text.length
  }

  fun addMessage(messageParts: List<MessagePart>, state: MessageState = MessageState.NORMAL): () -> Rectangle? {
    val lessonMessage = LessonMessage(messageParts, state)
    when (state) {
      MessageState.INACTIVE -> inactiveMessages
      MessageState.RESTORE -> restoreMessages
      else -> activeMessages
    }.add(lessonMessage)

    redrawMessages()

    return { getRectangleToScroll(lessonMessage) }
  }

  fun removeMessage(index: Int) {
    activeMessages.removeAt(index)
  }

  private fun getRectangleToScroll(lessonMessage: LessonMessage): Rectangle? {
    val startRect = modelToView(lessonMessage.start + 1) ?: return null
    val endRect = modelToView(lessonMessage.end - 1) ?: return null
    return Rectangle(startRect.x, startRect.y - activeTaskInset, endRect.x + endRect.width - startRect.x,
                     endRect.y + endRect.height - startRect.y + activeTaskInset * 2)
  }

  fun redrawMessages() {
    ranges.clear()
    text = ""
    insertOffset = 0
    for (lessonMessage in allLessonMessages()) {
      paragraphStyle = if (panelMode) TASK_PARAGRAPH_STYLE else BALLOON_STYLE
      val messageParts: List<MessagePart> = lessonMessage.messageParts
      lessonMessage.start = insertOffset
      if (insertOffset != 0)
        insertText("\n", REGULAR)
      for (part in messageParts) {
        val startOffset = insertOffset
        part.startOffset = startOffset
        when (part.type) {
          MessagePart.MessageType.TEXT_REGULAR -> insertText(part.text, REGULAR)
          MessagePart.MessageType.TEXT_BOLD -> insertText(part.text, BOLD)
          MessagePart.MessageType.SHORTCUT -> appendShortcut(part)?.let { ranges.add(it) }
          MessagePart.MessageType.CODE -> insertText(" ${part.text} ", CODE)
          MessagePart.MessageType.CHECK -> insertText(part.text, ROBOTO)
          MessagePart.MessageType.LINK -> appendLink(part)?.let { ranges.add(it) }
          MessagePart.MessageType.ICON_IDX -> LearningUiManager.iconMap[part.text]?.let { addPlaceholderForIcon(it) }
          MessagePart.MessageType.PROPOSE_RESTORE -> insertText(part.text, BOLD)
          MessagePart.MessageType.LINE_BREAK -> {
            insertText("\n", REGULAR)
            paragraphStyle = INTERNAL_PARAGRAPH_STYLE
          }
        }
        part.endOffset = insertOffset
      }
      lessonMessage.end = insertOffset
      if (lessonMessage.state == MessageState.INACTIVE) {
        setInactiveStyle(lessonMessage)
      }
    }
  }

  private fun addPlaceholderForIcon(icon: Icon) {
    var placeholder = " "
    while (this.getFontMetrics(this.font).stringWidth(placeholder) <= icon.iconWidth) {
      placeholder += " "
    }
    placeholder += " "
    insertText(placeholder, REGULAR)
  }

  fun passPreviousMessages() {
    for (message in activeMessages) {
      message.state = MessageState.PASSED
    }
    redrawMessages()
  }

  private fun setInactiveStyle(lessonMessage: LessonMessage) {
    styledDocument.setCharacterAttributes(lessonMessage.start, lessonMessage.end, INACTIVE, false)
  }

  fun clear() {
    text = ""
    activeMessages.clear()
    restoreMessages.clear()
    inactiveMessages.clear()
    ranges.clear()
  }

  /**
   * Appends link inside JTextPane to Run another lesson

   * @param messagePart - should have LINK type. message.runnable starts when the message has been clicked.
   */
  @Throws(BadLocationException::class)
  private fun appendLink(messagePart: MessagePart): RangeData? {
    val clickRange = appendClickableRange(messagePart.text, LINK)
    val runnable = messagePart.runnable ?: return null
    return RangeData(clickRange) { _, _ -> runnable.run() }
  }

  private fun appendShortcut(messagePart: MessagePart): RangeData? {
    val range = appendClickableRange(" ${messagePart.text} ", SHORTCUT)
    val actionId = messagePart.link ?: return null
    val clickRange = IntRange(range.first + 1, range.last - 1) // exclude around spaces
    return RangeData(clickRange) { p, h -> showShortcutBalloon(p, h, actionId) }
  }

  private fun showShortcutBalloon(point: Point, height: Int, actionName: String?) {
    if (actionName == null) return
    showActionKeyPopup(this, point, height, actionName)
  }

  private fun appendClickableRange(clickable: String, attributeSet: SimpleAttributeSet): IntRange {
    val startLink = insertOffset
    insertText(clickable, attributeSet)
    val endLink = insertOffset
    return startLink..endLink
  }

  override fun paintComponent(g: Graphics) {
    try {
      paintMessages(g)
    }
    catch (e: BadLocationException) {
      LOG.warn(e)
    }

    super.paintComponent(g)
    paintLessonCheckmarks(g)
  }

  private fun paintLessonCheckmarks(g: Graphics) {
    for (lessonMessage in allLessonMessages()) {
      var startOffset = lessonMessage.start
      if (startOffset != 0) startOffset++
      val rectangle = modelToView(startOffset)
      if (lessonMessage.state == MessageState.PASSED) {
        try {
          val checkmark = FeaturesTrainerIcons.Img.GreenCheckmark
          if (SystemInfo.isMac) {
            checkmark.paintIcon(this, g, rectangle.x - UISettings.instance.checkIndent, rectangle.y + JBUI.scale(1))
          }
          else {
            checkmark.paintIcon(this, g, rectangle.x - UISettings.instance.checkIndent, rectangle.y + JBUI.scale(1))
          }
        }
        catch (e: BadLocationException) {
          LOG.warn(e)
        }
      }
      else if (!LessonManager.instance.lessonIsRunning()) {
        AllIcons.General.Information.paintIcon(this, g, rectangle.x - UISettings.instance.checkIndent, rectangle.y + JBUI.scale(1))
      }
    }
  }

  @Throws(BadLocationException::class)
  private fun paintMessages(g: Graphics) {
    val g2d = g as Graphics2D
    for (lessonMessage in allLessonMessages()) {
      val myMessages = lessonMessage.messageParts
      for (myMessage in myMessages) {
        if (myMessage.type == MessagePart.MessageType.SHORTCUT) {
          val bg = UISettings.instance.shortcutBackgroundColor
          val needColor = if (lessonMessage.state == MessageState.INACTIVE) Color(bg.red, bg.green, bg.blue, 255 * 3 / 10) else bg
          drawRectangleAroundText(myMessage, g2d, needColor) { r2d ->
            g2d.fill(r2d)
          }
        }
        else if (myMessage.type == MessagePart.MessageType.CODE) {
          drawRectangleAroundText(myMessage, g2d, UISettings.instance.codeBorderColor) { r2d ->
            g2d.draw(r2d)
          }
        }
        else if (myMessage.type == MessagePart.MessageType.ICON_IDX) {
          val rect = modelToView2D(myMessage.startOffset + 1)
          val icon = LearningUiManager.iconMap[myMessage.text]
          icon?.paintIcon(this, g2d, rect.x.toInt(), rect.y.toInt())
        }
      }
    }
    val lastActiveMessage: LessonMessage? = activeMessages.lastOrNull()
    val lastPassedMessage: LessonMessage? = activeMessages.indexOfLast { it.state == MessageState.PASSED }
      .takeIf { it != -1 && it < activeMessages.size - 1 }
      ?.let { activeMessages[it + 1] }
    if (panelMode && lastActiveMessage != null && lastActiveMessage.state == MessageState.NORMAL) {
      val c = UISettings.instance.activeTaskBorder
      val a = if (totalAnimation == 0) 255 else 255*currentAnimation/totalAnimation
      val needColor = Color(c.red, c.green, c.blue, a)
      drawRectangleAroundMessage(lastPassedMessage, lastActiveMessage, g2d, needColor)
    }
  }

  private fun drawRectangleAroundText(myMessage: MessagePart,
                                      g2d: Graphics2D,
                                      needColor: Color,
                                      draw: (r2d: RoundRectangle2D) -> Unit) {
    val startOffset = myMessage.startOffset
    val endOffset = myMessage.endOffset
    val rectangleStart = modelToView2D(startOffset + 1)
    val rectangleEnd = modelToView2D(endOffset - 1)
    val color = g2d.color
    val fontSize = UISettings.instance.fontSize

    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val r2d: RoundRectangle2D = if (!SystemInfo.isMac)
      RoundRectangle2D.Double(rectangleStart.getX() - 2 * indent, rectangleStart.getY() - indent + JBUIScale.scale(2f),
                              rectangleEnd.getX() - rectangleStart.getX() + 4 * indent, (fontSize + 2 * indent).toDouble(),
                              arc.toDouble(), arc.toDouble())
    else
      RoundRectangle2D.Double(rectangleStart.getX() - 2 * indent, rectangleStart.getY() - indent + JBUIScale.scale(1f),
                              rectangleEnd.getX() - rectangleStart.getX() + 4 * indent, (fontSize + 2 * indent).toDouble(),
                              arc.toDouble(), arc.toDouble())
    draw(r2d)
    g2d.color = color
  }

  private fun drawRectangleAroundMessage(lastPassedMessage: LessonMessage? = null,
                                         lastActiveMessage: LessonMessage,
                                         g2d: Graphics2D,
                                         needColor: Color) {
    val startOffset = lastPassedMessage?.let { it.start + 1 } ?: 0
    val endOffset = lastActiveMessage.end

    val topLineX = modelToView2D(startOffset).x
    val topLineY = modelToView2D(startOffset).y
    val bottomLineY = modelToView2D(endOffset - 1).let { it.y + it.height }
    val textHeight = bottomLineY - topLineY
    val color = g2d.color

    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val xOffset = topLineX - activeTaskInset
    val yOffset = topLineY - activeTaskInset
    val width = this.bounds.width - activeTaskInset - xOffset - JBUIScale.scale(2) // 1 + 1 line width
    val height = textHeight + 2 * activeTaskInset - JBUIScale.scale(2)
    g2d.draw(RoundRectangle2D.Double(xOffset, yOffset, width, height, arc.toDouble(), arc.toDouble()))
    g2d.color = color
  }

  override fun getMaximumSize(): Dimension {
    return preferredSize
  }

  companion object {
    private val LOG = Logger.getInstance(LessonMessagePane::class.java)

    //Style Attributes for LessonMessagePane(JTextPane)
    private val INACTIVE = SimpleAttributeSet()
    private val REGULAR = SimpleAttributeSet()
    private val BOLD = SimpleAttributeSet()
    private val SHORTCUT = SimpleAttributeSet()
    private val ROBOTO = SimpleAttributeSet()
    private val CODE = SimpleAttributeSet()
    private val LINK = SimpleAttributeSet()

    private val TASK_PARAGRAPH_STYLE = SimpleAttributeSet()
    private val INTERNAL_PARAGRAPH_STYLE = SimpleAttributeSet()
    private val BALLOON_STYLE = SimpleAttributeSet()

    //arc & indent for shortcut back plate
    private val arc by lazy { JBUI.scale(4) }
    private val indent by lazy { JBUI.scale(2) }
    private val activeTaskInset by lazy { JBUI.scale(12) }
  }
}
