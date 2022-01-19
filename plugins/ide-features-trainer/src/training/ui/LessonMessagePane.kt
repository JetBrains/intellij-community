// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import training.FeaturesTrainerIcons
import training.dsl.TaskTextProperties
import training.learn.lesson.LessonManager
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.GlyphVector
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JTextPane
import javax.swing.text.AttributeSet
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import kotlin.math.roundToInt

internal class LessonMessagePane(private val panelMode: Boolean = true) : JTextPane() {
  //Style Attributes for LessonMessagePane(JTextPane)
  private val INACTIVE = SimpleAttributeSet()
  private val REGULAR = SimpleAttributeSet()
  private val BOLD = SimpleAttributeSet()
  private val SHORTCUT = SimpleAttributeSet()
  private val ROBOTO = SimpleAttributeSet()
  private val CODE = SimpleAttributeSet()
  private val LINK = SimpleAttributeSet()

  private var codeFontSize = UISettings.instance.fontSize.toInt()

  private val TASK_PARAGRAPH_STYLE = SimpleAttributeSet()
  private val INTERNAL_PARAGRAPH_STYLE = SimpleAttributeSet()
  private val BALLOON_STYLE = SimpleAttributeSet()

  private val textColor: Color = if (panelMode) UISettings.instance.defaultTextColor else UISettings.instance.tooltipTextColor
  private val codeForegroundColor: Color = if (panelMode) UISettings.instance.codeForegroundColor else UISettings.instance.tooltipTextColor

  enum class MessageState { NORMAL, PASSED, INACTIVE, RESTORE, INFORMER }

  data class MessageProperties(val state: MessageState = MessageState.NORMAL,
                               val visualIndex: Int? = null,
                               val useInternalParagraphStyle: Boolean = false,
                               val textProperties: TaskTextProperties? = null)

  private data class LessonMessage(
    val messageParts: List<MessagePart>,
    var state: MessageState,
    val visualIndex: Int?,
    val useInternalParagraphStyle: Boolean,
    val textProperties: TaskTextProperties?,
    var start: Int = 0,
    var end: Int = 0
  )

  private data class RangeData(var range: IntRange, val action: (Point, Int) -> Unit)

  private val activeMessages = mutableListOf<LessonMessage>()
  private val restoreMessages = mutableListOf<LessonMessage>()
  private val inactiveMessages = mutableListOf<LessonMessage>()

  private val fontFamily: String get() = StartupUiUtil.getLabelFont().fontName

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
        val rectangle = modelToView2D(middle)
        rangeData.action(Point(rectangle.x.roundToInt(), (rectangle.y.roundToInt() + rectangle.height.roundToInt() / 2)),
                         rectangle.height.roundToInt())
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
    val offset = viewToModel2D(Point2D.Double(me.x.toDouble(), me.y.toDouble()))
    val result = ranges.find { offset in it.range } ?: return null
    if (offset < 0 || offset >= document.length) return null
    for (i in result.range) {
      val rectangle = modelToView2D(i)
      if (me.x >= rectangle.x && me.y >= rectangle.y && me.y <= rectangle.y + rectangle.height) {
        return result
      }
    }
    return null
  }

  override fun addNotify() {
    super.addNotify()
    initStyleConstants()
    redrawMessages()
  }

  override fun updateUI() {
    super.updateUI()
    ApplicationManager.getApplication().invokeLater(Runnable {
      initStyleConstants()
      redrawMessages()
    })
  }

  private fun initStyleConstants() {
    val fontSize = UISettings.instance.fontSize.toInt()

    StyleConstants.setForeground(INACTIVE, UISettings.instance.inactiveColor)

    StyleConstants.setFontFamily(REGULAR, fontFamily)
    StyleConstants.setFontSize(REGULAR, fontSize)

    StyleConstants.setFontFamily(BOLD, fontFamily)
    StyleConstants.setFontSize(BOLD, fontSize)
    StyleConstants.setBold(BOLD, true)

    StyleConstants.setFontFamily(SHORTCUT, fontFamily)
    StyleConstants.setFontSize(SHORTCUT, fontSize)
    StyleConstants.setBold(SHORTCUT, true)

    EditorColorsManager.getInstance().globalScheme.editorFontName
    StyleConstants.setFontFamily(CODE, EditorColorsManager.getInstance().globalScheme.editorFontName)
    StyleConstants.setFontSize(CODE, codeFontSize)

    StyleConstants.setFontFamily(LINK, fontFamily)
    StyleConstants.setUnderline(LINK, true)
    StyleConstants.setFontSize(LINK, fontSize)

    StyleConstants.setSpaceAbove(TASK_PARAGRAPH_STYLE, UISettings.instance.taskParagraphAbove.toFloat())
    setCommonParagraphAttributes(TASK_PARAGRAPH_STYLE)

    StyleConstants.setSpaceAbove(INTERNAL_PARAGRAPH_STYLE, UISettings.instance.taskInternalParagraphAbove.toFloat())
    setCommonParagraphAttributes(INTERNAL_PARAGRAPH_STYLE)

    StyleConstants.setLineSpacing(BALLOON_STYLE, 0.2f)
    StyleConstants.setLeftIndent(BALLOON_STYLE, UISettings.instance.balloonIndent.toFloat())

    StyleConstants.setForeground(REGULAR, textColor)
    StyleConstants.setForeground(BOLD, textColor)
    StyleConstants.setForeground(SHORTCUT, UISettings.instance.shortcutTextColor)
    StyleConstants.setForeground(LINK, UISettings.instance.lessonLinkColor)
    StyleConstants.setForeground(CODE, codeForegroundColor)
  }

  private fun setCommonParagraphAttributes(attributeSet: SimpleAttributeSet) {
    StyleConstants.setLeftIndent(attributeSet, UISettings.instance.checkIndent.toFloat())
    StyleConstants.setRightIndent(attributeSet, 0f)
    StyleConstants.setSpaceBelow(attributeSet, 0.0f)
    StyleConstants.setLineSpacing(attributeSet, 0.2f)
  }

  fun messagesNumber(): Int = activeMessages.size

  @Suppress("SameParameterValue")
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

  fun getCurrentMessageRectangle(): Rectangle? {
    val lessonMessage = restoreMessages.lastOrNull() ?: activeMessages.lastOrNull() ?: return null
    return getRectangleToScroll(lessonMessage)
  }

  private fun insertText(text: String, attributeSet: AttributeSet) {
    document.insertString(insertOffset, text, attributeSet)
    styledDocument.setParagraphAttributes(insertOffset, text.length - 1, paragraphStyle, true)
    insertOffset += text.length
  }

  fun addMessage(messageParts: List<MessagePart>, properties: MessageProperties = MessageProperties()): () -> Rectangle? {
    val lessonMessage = LessonMessage(messageParts,
                                      properties.state,
                                      properties.visualIndex,
                                      properties.useInternalParagraphStyle,
                                      properties.textProperties,
    )
    when (properties.state) {
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
    val startRect = modelToView2D(lessonMessage.start + 1)?.toRectangle() ?: return null
    val endRect = modelToView2D(lessonMessage.end - 1)?.toRectangle() ?: return null
    return Rectangle(startRect.x, startRect.y - activeTaskInset, endRect.x + endRect.width - startRect.x,
                     endRect.y + endRect.height - startRect.y + activeTaskInset * 2)
  }

  fun redrawMessages() {
    initStyleConstants()
    ranges.clear()
    text = ""
    insertOffset = 0
    var previous: LessonMessage? = null
    for (lessonMessage in allLessonMessages()) {
      if (previous?.messageParts?.firstOrNull()?.type != MessagePart.MessageType.ILLUSTRATION) {
        val textProperties = previous?.textProperties
        paragraphStyle = when {
          textProperties != null -> {
            val customStyle = SimpleAttributeSet()
            setCommonParagraphAttributes(customStyle)
            StyleConstants.setSpaceAbove(customStyle, textProperties.spaceAbove.toFloat())
            StyleConstants.setSpaceBelow(customStyle, textProperties.spaceBelow.toFloat())
            customStyle
          }
          previous?.useInternalParagraphStyle == true -> INTERNAL_PARAGRAPH_STYLE
          panelMode -> TASK_PARAGRAPH_STYLE
          else -> BALLOON_STYLE
        }
      }
      val messageParts: List<MessagePart> = lessonMessage.messageParts
      lessonMessage.start = insertOffset
      if (insertOffset != 0)
        insertText("\n", paragraphStyle)
      for (part in messageParts) {
        val startOffset = insertOffset
        part.startOffset = startOffset
        when (part.type) {
          MessagePart.MessageType.TEXT_REGULAR -> insertText(part.text, REGULAR)
          MessagePart.MessageType.TEXT_BOLD -> insertText(part.text, BOLD)
          MessagePart.MessageType.SHORTCUT -> appendShortcut(part)?.let { ranges.add(it) }
          MessagePart.MessageType.CODE -> insertText(part.text, CODE)
          MessagePart.MessageType.CHECK -> insertText(part.text, ROBOTO)
          MessagePart.MessageType.LINK -> appendLink(part)?.let { ranges.add(it) }
          MessagePart.MessageType.ICON_IDX -> LearningUiManager.iconMap[part.text]?.let { addPlaceholderForIcon(it) }
          MessagePart.MessageType.PROPOSE_RESTORE -> insertText(part.text, BOLD)
          MessagePart.MessageType.ILLUSTRATION -> addPlaceholderForIllustration(part)
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
      previous = lessonMessage
    }
  }

  private fun addPlaceholderForIllustration(part: MessagePart) {
    val illustration = LearningUiManager.iconMap[part.text]
    if (illustration == null) {
      thisLogger().error("No illustration for ${part.text}")
    }
    else {
      val spaceAbove = spaceAboveIllustrationParagraph(illustration) + UISettings.instance.illustrationAbove
      val illustrationStyle = SimpleAttributeSet()
      StyleConstants.setSpaceAbove(illustrationStyle, spaceAbove.toFloat())
      setCommonParagraphAttributes(illustrationStyle)
      paragraphStyle = illustrationStyle
    }
    insertText(" ", REGULAR)
  }

  private fun spaceAboveIllustrationParagraph(illustration: Icon) = illustration.iconHeight - getFontMetrics(this.font).height + UISettings.instance.illustrationBelow

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
    val range = appendClickableRange(messagePart.text, SHORTCUT)
    val actionId = messagePart.link ?: return null
    val clickRange = IntRange(range.first + 1, range.last - 1) // exclude around spaces
    return RangeData(clickRange) { p, h -> showShortcutBalloon(p, h, actionId) }
  }

  private fun showShortcutBalloon(point: Point2D, height: Int, actionName: String?) {
    if (actionName == null) return
    showActionKeyPopup(this, point.toPoint(), height, actionName)
  }

  private fun appendClickableRange(clickable: String, attributeSet: SimpleAttributeSet): IntRange {
    val startLink = insertOffset
    insertText(clickable, attributeSet)
    val endLink = insertOffset
    return startLink..endLink
  }

  override fun paintComponent(g: Graphics) {
    adjustCodeFontSize(g)
    try {
      paintMessages(g)
    }
    catch (e: BadLocationException) {
      LOG.warn(e)
    }

    super.paintComponent(g)
    paintLessonCheckmarks(g)
    drawTaskNumbers(g)
  }

  private fun adjustCodeFontSize(g: Graphics) {
    val fontSize = StyleConstants.getFontSize(CODE)
    val labelFont = UISettings.instance.plainFont
    val (numberFont, _, _) = getNumbersFont(labelFont, g)
    if (numberFont.size != fontSize) {
      StyleConstants.setFontSize(CODE, numberFont.size)
      codeFontSize = numberFont.size
      redrawMessages()
    }
  }

  private fun paintLessonCheckmarks(g: Graphics) {
    val plainFont = UISettings.instance.plainFont
    val fontMetrics = g.getFontMetrics(plainFont)
    val height = if (g is Graphics2D) letterHeight(plainFont, g, "A") else fontMetrics.height
    val baseLineOffset = fontMetrics.ascent + fontMetrics.leading

    for (lessonMessage in allLessonMessages()) {
      var startOffset = lessonMessage.start
      if (startOffset != 0) startOffset++
      val rectangle = modelToView2D(startOffset).toRectangle()
      if (lessonMessage.messageParts.singleOrNull()?.type == MessagePart.MessageType.ILLUSTRATION) {
        continue
      }
      val icon = if (lessonMessage.state == MessageState.PASSED) {
        FeaturesTrainerIcons.Img.GreenCheckmark
      }
      else if (!LessonManager.instance.lessonIsRunning()) {
        AllIcons.General.Information
      }
      else continue
      val xShift = icon.iconWidth + UISettings.instance.numberTaskIndent
      val y = rectangle.y + baseLineOffset - (height + icon.iconHeight + 1) / 2
      icon.paintIcon(this, g, rectangle.x - xShift, y)
    }
  }

  private data class FontSearchResult(val numberFont: Font, val numberHeight: Int, val textLetterHeight: Int)

  private fun drawTaskNumbers(g: Graphics) {
    val oldFont = g.font
    val labelFont = UISettings.instance.plainFont
    val (numberFont, numberHeight, textLetterHeight) = getNumbersFont(labelFont, g)
    val textFontMetrics = g.getFontMetrics(labelFont)
    val baseLineOffset = textFontMetrics.ascent + textFontMetrics.leading
    g.font = numberFont

    fun paintNumber(lessonMessage: LessonMessage, color: Color) {
      var startOffset = lessonMessage.start
      if (startOffset != 0) startOffset++

      val s = lessonMessage.visualIndex?.toString()?.padStart(2, '0') ?: return
      val width = textFontMetrics.stringWidth(s)

      val modelToView2D = modelToView2D(startOffset)
      val rectangle = modelToView2D.toRectangle()
      val xOffset = rectangle.x - (width + UISettings.instance.numberTaskIndent)
      val baseLineY = rectangle.y + baseLineOffset
      val yOffset = baseLineY + (numberHeight - textLetterHeight)
      val backupColor = g.color
      g.color = color
      GraphicsUtil.setupAAPainting(g)
      g.drawString(s, xOffset, yOffset)
      g.color = backupColor
    }
    for (lessonMessage in inactiveMessages) {
      paintNumber(lessonMessage, UISettings.instance.futureTaskNumberColor)
    }
    if (activeMessages.lastOrNull()?.state != MessageState.PASSED || !panelMode) { // lesson can be opened as passed
      val firstActiveMessage = firstActiveMessage()
      if (firstActiveMessage != null) {
        val color = if (panelMode) UISettings.instance.activeTaskNumberColor else UISettings.instance.tooltipTaskNumberColor
        paintNumber(firstActiveMessage, color)
      }
    }
    g.font = oldFont
  }

  private fun getNumbersFont(textFont: Font, g: Graphics): FontSearchResult {
    val style = Font.PLAIN
    val monoFontName = FontPreferences.DEFAULT_FONT_NAME

    if (g is Graphics2D) {
      val textHeight = letterHeight(textFont, g, "A")

      var size = textFont.size
      var numberHeight = 0
      lateinit var numberFont: Font

      fun calculateHeight(): Int {
        numberFont = Font(monoFontName, style, size)
        numberHeight = letterHeight(numberFont, g, "0")
        return numberHeight
      }

      calculateHeight()
      if (numberHeight > textHeight) {
        size--
        while (calculateHeight() >= textHeight) {
          size--
        }
        size++
        calculateHeight()
      }
      else if (numberHeight < textHeight) {
        size++
        while (calculateHeight() < textHeight) {
          size++
        }
      }
      return FontSearchResult(numberFont, numberHeight, textHeight)
    }

    return FontSearchResult(Font(monoFontName, style, textFont.size), 0, 0)
  }

  private fun letterHeight(font: Font, g: Graphics2D, str: String): Int {
    val gv: GlyphVector = font.createGlyphVector(g.fontRenderContext, str)
    return gv.getGlyphMetrics(0).bounds2D.height.roundToInt()
  }

  @Throws(BadLocationException::class)
  private fun paintMessages(g: Graphics) {
    val g2d = g as Graphics2D
    for (lessonMessage in allLessonMessages()) {
      val myMessages = lessonMessage.messageParts
      for (myMessage in myMessages) {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (myMessage.type) {
          MessagePart.MessageType.SHORTCUT -> {
            val bg = UISettings.instance.shortcutBackgroundColor
            val needColor = if (lessonMessage.state == MessageState.INACTIVE) Color(bg.red, bg.green, bg.blue, 255 * 3 / 10) else bg

            for (part in myMessage.splitMe()) {
              drawRectangleAroundText(part, g2d, needColor) { r2d ->
                g2d.fill(r2d)
              }
            }
          }
          MessagePart.MessageType.CODE -> {
            val needColor = UISettings.instance.codeBorderColor
            drawRectangleAroundText(myMessage, g2d, needColor) { r2d ->
              if (panelMode) {
                g2d.draw(r2d)
              }
              else {
                g2d.fill(r2d)
              }
            }
          }
          MessagePart.MessageType.ICON_IDX -> {
            val rect = modelToView2D(myMessage.startOffset)
            var icon = LearningUiManager.iconMap[myMessage.text] ?: continue
            if (inactiveMessages.contains(lessonMessage)) {
              icon = getInactiveIcon(icon)
            }
            icon.paintIcon(this, g2d, (rect.x + JBUIScale.scale(1f)).toInt(), rect.y.toInt())
          }
          MessagePart.MessageType.ILLUSTRATION -> {
            val x = modelToView2D(myMessage.startOffset).x.toInt()
            val y = modelToView2D(myMessage.endOffset - 1).y.toInt()
            var icon = LearningUiManager.iconMap[myMessage.text] ?: continue
            if (inactiveMessages.contains(lessonMessage)) {
              icon = getInactiveIcon(icon)
            }
            icon.paintIcon(this, g2d, x, y - spaceAboveIllustrationParagraph(icon))
          }
        }
      }
    }
    val lastActiveMessage = activeMessages.lastOrNull()
    val firstActiveMessage = firstActiveMessage()
    if (panelMode && lastActiveMessage != null && lastActiveMessage.state == MessageState.NORMAL) {
      val c = UISettings.instance.activeTaskBorder
      val a = if (totalAnimation == 0) 255 else 255 * currentAnimation / totalAnimation
      val needColor = Color(c.red, c.green, c.blue, a)
      drawRectangleAroundMessage(firstActiveMessage, lastActiveMessage, g2d, needColor)
    }
  }

  private fun getInactiveIcon(icon: Icon) = WatermarkIcon(icon, UISettings.instance.transparencyInactiveFactor.toFloat())

  private fun firstActiveMessage(): LessonMessage? = activeMessages.indexOfLast { it.state == MessageState.PASSED }
                                                       .takeIf { it != -1 && it < activeMessages.size - 1 }
                                                       ?.let { activeMessages[it + 1] } ?: activeMessages.firstOrNull()

  private fun drawRectangleAroundText(myMessage: MessagePart,
                                      g2d: Graphics2D,
                                      needColor: Color,
                                      draw: (r2d: RoundRectangle2D) -> Unit) {
    val startOffset = myMessage.startOffset
    val endOffset = myMessage.endOffset
    val rectangleStart = modelToView2D(startOffset)
    val rectangleEnd = modelToView2D(endOffset)
    val color = g2d.color
    val fontSize = UISettings.instance.fontSize

    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val shift = if (SystemInfo.isMac) 1f else 2f
    val r2d = RoundRectangle2D.Double(rectangleStart.x - 2 * indent, rectangleStart.y - indent + JBUIScale.scale(shift),
                                      rectangleEnd.x - rectangleStart.x + 4 * indent, (fontSize + 2 * indent).toDouble(),
                                      arc.toDouble(), arc.toDouble())
    draw(r2d)
    g2d.color = color
  }

  private fun drawRectangleAroundMessage(lastPassedMessage: LessonMessage? = null,
                                         lastActiveMessage: LessonMessage,
                                         g2d: Graphics2D,
                                         needColor: Color) {
    val startOffset = lastPassedMessage?.let { if (it.start == 0) 0 else it.start + 1 } ?: 0
    val endOffset = lastActiveMessage.end

    val topLineY = modelToView2D(startOffset).y
    val bottomLineY = modelToView2D(endOffset - 1).let { it.y + it.height }
    val textHeight = bottomLineY - topLineY
    val color = g2d.color

    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val xOffset = JBUI.scale(2).toDouble()
    val yOffset = topLineY - activeTaskInset
    val width = this.bounds.width - 2*xOffset - JBUIScale.scale(2) // 1 + 1 line width
    val height = textHeight + 2 * activeTaskInset - JBUIScale.scale(2) + (lastActiveMessage.textProperties?.spaceBelow ?: 0)
    g2d.draw(RoundRectangle2D.Double(xOffset, yOffset, width, height, arc.toDouble(), arc.toDouble()))
    g2d.color = color
  }

  override fun getMaximumSize(): Dimension {
    return preferredSize
  }

  companion object {
    private val LOG = Logger.getInstance(LessonMessagePane::class.java)

    //arc & indent for shortcut back plate
    private val arc by lazy { JBUI.scale(4) }
    private val indent by lazy { JBUI.scale(2) }
    private val activeTaskInset by lazy { JBUI.scale(12) }

    private fun Point2D.toPoint(): Point {
      return Point(x.roundToInt(), y.roundToInt())
    }

    private fun Rectangle2D.toRectangle(): Rectangle {
      return Rectangle(x.roundToInt(), y.roundToInt(), width.roundToInt(), height.roundToInt())
    }
  }
}
