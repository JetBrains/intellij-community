// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.text.StyledTextPane
import com.intellij.ide.ui.text.paragraph.TextParagraph
import com.intellij.ide.ui.text.parts.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WatermarkIcon
import training.FeaturesTrainerIcons
import training.dsl.TaskTextProperties
import training.learn.lesson.LessonManager
import java.awt.*
import java.awt.font.GlyphVector
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JTextPane
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import kotlin.math.roundToInt

internal class LessonMessagePane(private val panelMode: Boolean = true) : StyledTextPane() {
  private val textColor: Color = if (panelMode) UISettings.getInstance().defaultTextColor else UISettings.getInstance().tooltipTextColor
  private val codeForegroundColor: Color = if (panelMode) UISettings.getInstance().codeForegroundColor else UISettings.getInstance().tooltipTextColor
  private val shortcutTextColor = if (panelMode) UISettings.getInstance().shortcutTextColor else UISettings.getInstance().tooltipShortcutTextColor

  enum class MessageState { NORMAL, PASSED, INACTIVE, RESTORE, INFORMER }

  data class MessageProperties(val state: MessageState = MessageState.NORMAL,
                               val visualIndex: Int? = null,
                               val useInternalParagraphStyle: Boolean = false,
                               val textProperties: TaskTextProperties? = null)

  private inner class LessonMessage(
    textParts: List<TextPart>,
    var state: MessageState,
    val visualIndex: Int?,
    val useInternalParagraphStyle: Boolean,
    val textProperties: TaskTextProperties?,
  ) : TextParagraph(textParts) {
    private val isIllustration: Boolean = textParts.singleOrNull() is IllustrationTextPart

    override val attributes: SimpleAttributeSet
      get() = SimpleAttributeSet().apply {
        if (panelMode) {
          StyleConstants.setLeftIndent(this, UISettings.getInstance().checkIndent.toFloat())
          StyleConstants.setRightIndent(this, 0f)
          StyleConstants.setSpaceBelow(this, 0.0f)
        }
        else {
          StyleConstants.setLeftIndent(this, UISettings.getInstance().balloonIndent.toFloat())
        }

        if (isIllustration) {
          // it is required to not add extra space below the image
          StyleConstants.setLineSpacing(this, 0f)
        }
        else StyleConstants.setLineSpacing(this, 0.3f)

        val properties = textProperties
        if (properties != null) {
          StyleConstants.setSpaceAbove(this, properties.spaceAbove.toFloat())
          StyleConstants.setSpaceBelow(this, properties.spaceBelow.toFloat())
        }
        else if (useInternalParagraphStyle || isIllustration) {
          StyleConstants.setSpaceAbove(this, UISettings.getInstance().taskInternalParagraphAbove.toFloat())
        }
        else if (panelMode) {
          StyleConstants.setSpaceAbove(this, UISettings.getInstance().taskParagraphAbove.toFloat())
        }
      }

    private val inactiveAttributes: SimpleAttributeSet
      get() = SimpleAttributeSet().apply { StyleConstants.setForeground(this, UISettings.getInstance().inactiveColor) }

    override fun insertToDocument(textPane: JTextPane, startOffset: Int, isLast: Boolean): Int {
      val endOffset = super.insertToDocument(textPane, startOffset, isLast)
      if (state == MessageState.INACTIVE) {
        textPane.styledDocument.setCharacterAttributes(startOffset, endOffset, inactiveAttributes, false)
      }
      return endOffset
    }

    override fun insertTextPart(textPart: TextPart, textPane: JTextPane, startOffset: Int): Int {
      editShortcutBackground(textPart)
      editIcons(textPart)
      editForeground(textPart)
      return super.insertTextPart(textPart, textPane, startOffset)
    }

    private fun editShortcutBackground(textPart: TextPart) {
      if (textPart is ShortcutTextPart) {
        textPart.backgroundColor = if (panelMode) {
          UISettings.getInstance().shortcutBackgroundColor
        }
        else UISettings.getInstance().tooltipShortcutBackgroundColor
      }
    }

    private fun editIcons(textPart: TextPart) {
      if (state == MessageState.INACTIVE && (textPart is IllustrationTextPart || textPart is IconTextPart)) {
        textPart.editAttributes {
          val icon = StyleConstants.getIcon(this)
          StyleConstants.setIcon(this, getInactiveIcon(icon))
        }
      }
    }

    private fun editForeground(textPart: TextPart) {
      val foregroundColor = when (textPart) {
        is CodeTextPart -> codeForegroundColor
        is ShortcutTextPart -> shortcutTextColor
        is RegularTextPart -> textColor
        else -> null
      }
      if (foregroundColor != null) {
        textPart.editAttributes {
          StyleConstants.setForeground(this, foregroundColor)
        }
      }
    }
  }

  override var paragraphs: List<TextParagraph> = emptyList()
    get() = activeMessages + restoreMessages + inactiveMessages
    @Deprecated("Use 'addMessage' and 'clear' methods")
    set(value) {
      field = value
      redraw()
      repaint()
    }

  private val activeMessages = mutableListOf<LessonMessage>()
  private val restoreMessages = mutableListOf<LessonMessage>()
  private val inactiveMessages = mutableListOf<LessonMessage>()

  var currentAnimation = 0
  var totalAnimation = 0

  fun messagesNumber(): Int = activeMessages.size

  @Suppress("SameParameterValue")
  private fun removeMessagesRange(startIdx: Int, endIdx: Int, list: MutableList<LessonMessage>) {
    if (startIdx == endIdx) return
    list.subList(startIdx, endIdx).clear()
  }

  fun clearRestoreMessages(): () -> Rectangle? {
    removeMessagesRange(0, restoreMessages.size, restoreMessages)
    redraw()
    val lastOrNull = activeMessages.lastOrNull()
    return { lastOrNull?.let { getRectangleToScroll(it) } }
  }

  fun removeInactiveMessages(number: Int) {
    removeMessagesRange(0, number, inactiveMessages)
    redraw()
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

  fun addMessage(message: TextParagraph, properties: MessageProperties = MessageProperties()): () -> Rectangle? {
    val lessonMessage = LessonMessage(
      message.textParts,
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

    redraw()

    return { getRectangleToScroll(lessonMessage) }
  }

  fun removeMessage(index: Int) {
    activeMessages.removeAt(index)
  }

  private fun getRectangleToScroll(lessonMessage: LessonMessage): Rectangle? {
    val range = getParagraphRange(lessonMessage)
    val startRect = modelToView2D(range.first)?.toRectangle() ?: return null
    val endRect = modelToView2D(range.last - 1)?.toRectangle() ?: return null
    return Rectangle(startRect.x, startRect.y - activeTaskInset, endRect.x + endRect.width - startRect.x,
                     endRect.y + endRect.height - startRect.y + activeTaskInset * 2)
  }

  fun passPreviousMessages() {
    for (message in activeMessages) {
      message.state = MessageState.PASSED
    }
    redraw()
  }

  fun clearMessages() {
    clear()
    activeMessages.clear()
    restoreMessages.clear()
    inactiveMessages.clear()
  }

  override fun paintComponent(g: Graphics) {
    try {
      highlightActiveMessage(g)
    }
    catch (e: BadLocationException) {
      LOG.warn(e)
    }

    super.paintComponent(g)
    paintLessonCheckmarks(g)
    drawTaskNumbers(g)
  }

  private fun paintLessonCheckmarks(g: Graphics) {
    val plainFont = UISettings.getInstance().plainFont
    val fontMetrics = g.getFontMetrics(plainFont)
    val height = if (g is Graphics2D) letterHeight(plainFont, g, "A") else fontMetrics.height
    val baseLineOffset = fontMetrics.ascent + fontMetrics.leading

    for (lessonMessage in paragraphs) {
      lessonMessage as LessonMessage
      val range = getParagraphRange(lessonMessage)
      val rectangle = modelToView2D(range.first).toRectangle()
      if (lessonMessage.textParts.singleOrNull() is IllustrationTextPart) {
        continue
      }
      val icon = if (lessonMessage.state == MessageState.PASSED) {
        FeaturesTrainerIcons.GreenCheckmark
      }
      else if (!LessonManager.instance.lessonIsRunning()) {
        AllIcons.General.Information
      }
      else continue
      val xShift = icon.iconWidth + UISettings.getInstance().numberTaskIndent
      val y = rectangle.y + baseLineOffset - (height + icon.iconHeight + 1) / 2
      icon.paintIcon(this, g, rectangle.x - xShift, y)
    }
  }

  private data class FontSearchResult(val numberFont: Font, val numberHeight: Int, val textLetterHeight: Int)

  private fun drawTaskNumbers(g: Graphics) {
    val oldFont = g.font
    val labelFont = UISettings.getInstance().plainFont
    val (numberFont, numberHeight, textLetterHeight) = getNumbersFont(labelFont, g)
    val textFontMetrics = g.getFontMetrics(labelFont)
    val baseLineOffset = textFontMetrics.ascent + textFontMetrics.leading
    g.font = numberFont

    fun paintNumber(lessonMessage: LessonMessage, color: Color) {
      val s = lessonMessage.visualIndex?.toString()?.padStart(2, '0') ?: return
      val width = textFontMetrics.stringWidth(s)

      val range = getParagraphRange(lessonMessage)
      val modelToView2D = modelToView2D(range.first)
      val rectangle = modelToView2D.toRectangle()
      val xOffset = rectangle.x - (width + UISettings.getInstance().numberTaskIndent)
      val baseLineY = rectangle.y + baseLineOffset
      val yOffset = baseLineY + (numberHeight - textLetterHeight)
      val backupColor = g.color
      g.color = color
      GraphicsUtil.setupAAPainting(g)
      g.drawString(s, xOffset, yOffset)
      g.color = backupColor
    }
    for (lessonMessage in inactiveMessages) {
      paintNumber(lessonMessage, UISettings.getInstance().futureTaskNumberColor)
    }
    if (activeMessages.lastOrNull()?.state != MessageState.PASSED || !panelMode) { // lesson can be opened as passed
      val firstActiveMessage = firstActiveMessage()
      if (firstActiveMessage != null) {
        val color = if (panelMode) UISettings.getInstance().activeTaskNumberColor else UISettings.getInstance().tooltipTaskNumberColor
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
  private fun highlightActiveMessage(g: Graphics) {
    val g2d = g as Graphics2D
    val lastActiveMessage = activeMessages.lastOrNull()
    val firstActiveMessage = firstActiveMessage()
    if (panelMode && lastActiveMessage != null && lastActiveMessage.state == MessageState.NORMAL) {
      val c = UISettings.getInstance().activeTaskBorder
      val a = if (totalAnimation == 0) 255 else 255 * currentAnimation / totalAnimation
      val needColor = Color(c.red, c.green, c.blue, a)
      drawRectangleAroundMessage(firstActiveMessage, lastActiveMessage, g2d, needColor)
    }
  }

  private fun getInactiveIcon(icon: Icon) = WatermarkIcon(icon, UISettings.getInstance().transparencyInactiveFactor.toFloat())

  private fun firstActiveMessage(): LessonMessage? = activeMessages.indexOfLast { it.state == MessageState.PASSED }
                                                       .takeIf { it != -1 && it < activeMessages.size - 1 }
                                                       ?.let { activeMessages[it + 1] } ?: activeMessages.firstOrNull()

  private fun drawRectangleAroundMessage(lastPassedMessage: LessonMessage? = null,
                                         lastActiveMessage: LessonMessage,
                                         g2d: Graphics2D,
                                         needColor: Color) {
    val startOffset = lastPassedMessage?.let { getParagraphRange(it).first } ?: 0
    val endOffset = getParagraphRange(lastActiveMessage).last - 1

    val topLineY = modelToView2D(startOffset).y
    val bottomLineY = modelToView2D(endOffset).let { it.y + it.height }
    val textHeight = bottomLineY - topLineY
    val color = g2d.color

    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val xOffset = JBUI.scale(2).toDouble()
    val yOffset = topLineY - activeTaskInset
    val width = this.bounds.width - 2 * xOffset - JBUIScale.scale(2) // 1 + 1 line width
    val height = textHeight + 2 * activeTaskInset - JBUIScale.scale(2) + (lastActiveMessage.textProperties?.spaceBelow ?: 0)
    g2d.draw(RoundRectangle2D.Double(xOffset, yOffset, width, height, arc.toDouble(), arc.toDouble()))
    g2d.color = color
  }

  companion object {
    private val LOG = Logger.getInstance(LessonMessagePane::class.java)

    private val arc by lazy { JBUI.scale(4) }
    private val activeTaskInset by lazy { JBUI.scale(12) }

    private fun Rectangle2D.toRectangle(): Rectangle {
      return Rectangle(x.roundToInt(), y.roundToInt(), width.roundToInt(), height.roundToInt())
    }
  }
}
