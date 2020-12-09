// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.FeaturesTrainerIcons
import training.keymap.KeymapUtil
import training.learn.LearnBundle
import training.util.invokeActionForFocusContext
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class LessonMessagePane : JTextPane() {
  enum class MessageState { NORMAL, PASSED, INACTIVE, RESTORE, INFORMER }

  private data class LessonMessage(
    val messageParts: List<MessagePart>,
    var state: MessageState,
    var start: Int = 0,
    var end: Int = 0
  )

  private data class RangeData(var range: IntRange, val action: (Point) -> Unit)

  private val activeMessages = mutableListOf<LessonMessage>()
  private val restoreMessages = mutableListOf<LessonMessage>()
  private val inactiveMessages = mutableListOf<LessonMessage>()

  private val fontFamily: String = UIUtil.getLabelFont().fontName

  private val ranges = mutableSetOf<RangeData>()

  private var insertOffset: Int = 0

  private fun allLessonMessages() = activeMessages + restoreMessages + inactiveMessages

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
        rangeData.action(Point(rectangle.x, (rectangle.y + rectangle.height)))
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
    StyleConstants.setSpaceAbove(TASK_PARAGRAPH_STYLE, 20.0f)
    StyleConstants.setSpaceBelow(TASK_PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setLineSpacing(TASK_PARAGRAPH_STYLE, 0.2f)

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

  fun clearRestoreMessages() {
    removeMessagesRange(0, restoreMessages.size, restoreMessages)
    redrawMessages()
  }

  fun removeInactiveMessages(number: Int) {
    removeMessagesRange(0, number, inactiveMessages)
    redrawMessages()
  }

  fun resetMessagesNumber(number: Int) {
    val move = activeMessages.subList(number, activeMessages.size)
    move.forEach {
      it.state = MessageState.INACTIVE
    }
    inactiveMessages.addAll(0, move)
    move.clear()
    clearRestoreMessages()
  }

  private fun insertText(text: String, attributeSet: AttributeSet) {
    document.insertString(insertOffset, text, attributeSet)
    styledDocument.setParagraphAttributes(insertOffset, insertOffset + text.length - 1, TASK_PARAGRAPH_STYLE, true)
    insertOffset += text.length
  }

  fun addMessage(messageParts: List<MessagePart>, state: MessageState = MessageState.NORMAL): Rectangle? {
    val lessonMessage = LessonMessage(messageParts, state)
    when (state) {
      MessageState.INACTIVE -> inactiveMessages
      MessageState.RESTORE -> restoreMessages
      else -> activeMessages
    }.add(lessonMessage)

    redrawMessages()

    val startRect = modelToView(lessonMessage.start) ?: return null
    val endRect = modelToView(lessonMessage.end - 1) ?: return null
    return Rectangle(startRect.x, startRect.y, endRect.x + endRect.width - startRect.x, endRect.y + endRect.height - startRect.y)
  }

  fun redrawMessages() {
    ranges.clear()
    text = ""
    insertOffset = 0
    for (lessonMessage in allLessonMessages()) {
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
          MessagePart.MessageType.SHORTCUT -> appendShortcut(part).let { ranges.add(it) }
          MessagePart.MessageType.CODE -> insertText(" ${part.text} ", CODE)
          MessagePart.MessageType.CHECK -> insertText(part.text, ROBOTO)
          MessagePart.MessageType.LINK -> appendLink(part)?.let { ranges.add(it) }
          MessagePart.MessageType.ICON_IDX -> LearningUiManager.iconMap[part.text]?.let { addPlaceholderForIcon(it) }
          MessagePart.MessageType.PROPOSE_RESTORE -> insertText(part.text, BOLD)
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
    return RangeData(clickRange) { runnable.run() }
  }

  private fun appendShortcut(messagePart: MessagePart): RangeData {
    val range = appendClickableRange(" ${messagePart.text} ", SHORTCUT)
    val clickRange = IntRange(range.first + 1, range.last - 1) // exclude around spaces
    return RangeData(clickRange) { showShortcutBalloon(it, messagePart.link, messagePart.text) }
  }

  private fun showShortcutBalloon(it: Point, actionName: String?, shortcut: String) {
    lateinit var balloon: Balloon
    val jPanel = JPanel()
    jPanel.layout = BoxLayout(jPanel, BoxLayout.Y_AXIS)
    if (SystemInfo.isMac) {
      jPanel.add(JLabel(KeymapUtil.decryptMacShortcut(shortcut)))
    }
    val action = actionName?.let { ActionManager.getInstance().getAction(it) }
    if (action != null) {
      jPanel.add(JLabel(action.templatePresentation.text))
      jPanel.add(LinkLabel<Any>(LearnBundle.message("shortcut.balloon.apply.this.action"), null) { _, _ ->
        invokeActionForFocusContext(action)
        balloon.hide()
      })
      jPanel.add(LinkLabel<Any>(LearnBundle.message("shortcut.balloon.add.shortcut"), null) { _, _ ->
        KeymapPanel.addKeyboardShortcut(actionName, ActionShortcutRestrictions.getInstance().getForActionId(actionName),
                                        KeymapManager.getInstance().activeKeymap, this)
        balloon.hide()
        repaint()
      })
    }
    val builder = JBPopupFactory.getInstance()
      .createDialogBalloonBuilder(jPanel, null)
      //.setRequestFocus(true)
      .setHideOnClickOutside(true)
      .setCloseButtonEnabled(true)
      .setAnimationCycle(0)
      .setBlockClicksThroughBalloon(true)
    //.setContentInsets(Insets(0, 0, 0, 0))
    builder.setBorderColor(JBColor(Color.BLACK, Color.WHITE))
    balloon = builder.createBalloon()
    balloon.show(RelativePoint(this, it), Balloon.Position.below)
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
      if (lessonMessage.state == MessageState.PASSED) {
        var startOffset = lessonMessage.start
        if (startOffset != 0) startOffset++
        try {
          val rectangle = modelToView(startOffset)
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
    if (lastActiveMessage != null && lastActiveMessage.state == MessageState.NORMAL) {
      drawRectangleAroundMessage(lastPassedMessage, lastActiveMessage, g2d, UISettings.instance.activeTaskBorder)
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

    val r2d: RoundRectangle2D = if (!SystemInfo.isMac)
      RoundRectangle2D.Double(topLineX - activeTaskInset, topLineY - activeTaskInset - JBUIScale.scale(1),
                              this.bounds.width - UISettings.instance.checkIndent.toDouble() - insets.right * 1.0f + 2 * activeTaskInset, textHeight + 2 * activeTaskInset - JBUIScale.scale(2),
                              arc.toDouble(), arc.toDouble())
    else
      RoundRectangle2D.Double(topLineX - activeTaskInset, topLineY - activeTaskInset - JBUIScale.scale(1),
                              this.bounds.width - UISettings.instance.checkIndent.toDouble() - insets.right * 1.0f + 2 * activeTaskInset, textHeight + 2 * activeTaskInset - JBUIScale.scale(2),
                              arc.toDouble(), arc.toDouble())
    g2d.draw(r2d)
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

    //arc & indent for shortcut back plate
    private val arc by lazy { JBUI.scale(4) }
    private val indent by lazy { JBUI.scale(2) }
    private val activeTaskInset by lazy { JBUI.scale(8) }
  }
}
