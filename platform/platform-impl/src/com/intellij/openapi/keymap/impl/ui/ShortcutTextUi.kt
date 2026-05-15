// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.PressureShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.UIManager

/**
 * @author Alexander Lobas
 */
internal class ShortcutTextList {

  private val width: Int
  private val height = JBUI.scale(18)
  private val round = JBUI.scale(8)
  private val elements = mutableListOf<BaseElement>()
  val tree: JComponent

  constructor(shortcuts: Array<Shortcut>?, abbreviations: Collection<String>?, tree: JComponent, maxWidth: Int) {
    this.tree = tree
    if (shortcuts.isNullOrEmpty() && abbreviations.isNullOrEmpty()) {
      width = 0
      return
    }

    val fixedWidth = JBUI.scale(20)
    val textGap = JBUI.scale(4)
    val betweenGapElement = GapElement(JBUI.scale(3))

    val font = JBFont.medium()
    val fontMetrics = tree.getFontMetrics(font)
    val boldFont = font.asBold()

    val shortcutPresentation = createPresentation(
      textColor = JBColor.namedColor("Shortcut.foreground", JBColor(0x0, 0xDFE1E5)),
      fillColor = JBColor.namedColor("Shortcut.background", JBUI.CurrentTheme.CustomFrameDecorations.paneBackground()),
      borderColorKey = "Shortcut.borderColor",
      font = boldFont,
    )

    val abbreviationPresentation = createPresentation(
      textColor = JBColor.namedColor("Abbreviation.foreground", JBColor(0x5A5D6B2E, 0xB4B8BF40.toInt())),
      fillColor = getColor("Abbreviation.background"),
      borderColorKey = "Abbreviation.borderColor",
      font = boldFont,
    )

    val textSeparatorPresentation = ShortcutTextPresentation(JBUI.CurrentTheme.Label.foreground(), null, null, font)
    val orSeparator = createTextElement(KeyMapBundle.message("or.separator"), textSeparatorGap, tree, font, textSeparatorPresentation)
    val shortcutsBlocks = mutableListOf<List<BaseElement>>()

    if (!shortcuts.isNullOrEmpty()) {
      for (shortcut in shortcuts) {
        val texts = getShortcutTexts(shortcut)
        val lastText = texts.lastIndex
        val shortcutElements = mutableListOf<BaseElement>()

        if (shortcutsBlocks.isNotEmpty()) {
          shortcutElements.add(orSeparator)
        }

        for ((index, text) in texts.withIndex()) {
          if (text.length == 1) {
            shortcutElements.add(ShortcutTextElement(fixedWidth, text, shortcutPresentation))
          }
          else {
            shortcutElements.add(createTextElement(text, textGap, tree, boldFont, shortcutPresentation))
          }

          if (index < lastText) {
            shortcutElements.add(betweenGapElement)
          }
        }

        shortcutsBlocks.add(shortcutElements)
      }
    }

    if (!abbreviations.isNullOrEmpty()) {
      for (abbreviation in abbreviations) {
        val shortcutElements = mutableListOf<BaseElement>()
        if (shortcutsBlocks.isNotEmpty()) {
          shortcutElements.add(orSeparator)
        }

        shortcutElements.add(createTextElement(abbreviation, textGap, tree, boldFont, abbreviationPresentation))
        shortcutsBlocks.add(shortcutElements)
      }
    }

    fillElements(elements, shortcutsBlocks, maxWidth, fontMetrics, textSeparatorPresentation)
    width = elements.sumOf { element -> element.width }
  }

  private fun getShortcutTexts(shortcut: Shortcut): List<String> {
    if (shortcut is KeyboardShortcut) {
      return buildList {
        addShortcut(this, shortcut.firstKeyStroke)
        addShortcut(this, shortcut.secondKeyStroke)
      }
    }
    if (shortcut is PressureShortcut) {
      return listOf(shortcut.toString())
    }
    if (shortcut is MouseShortcut) {
      val modifiers = shortcut.modifiers
      if (modifiers > 0) {
        return buildList {
          addShortcutModifiers(this, modifiers)
          add(KeymapUtil.getShortcutText(MouseShortcut(shortcut.button, 0, shortcut.clickCount)))
        }
      }
    }
    return listOf(KeymapUtil.getShortcutText(shortcut))
  }

  private fun addShortcut(result: MutableList<String>, keyStroke: KeyStroke?) {
    if (keyStroke == null) {
      return
    }
    val modifiers = keyStroke.modifiers
    if (modifiers > 0) {
      addShortcutModifiers(result, modifiers)
      result.add(KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(keyStroke.keyCode, 0, keyStroke.isOnKeyRelease)))
    }
    else {
      result.add(KeymapUtil.getKeystrokeText(keyStroke))
    }
  }

  private fun addShortcutModifiers(result: MutableList<String>, modifiers: Int) {
    val modifiersText = KeymapUtil.getModifiersText(modifiers)
    if (KeymapUtil.isSimplifiedMacShortcuts || !ClientSystemInfo.isMac()) {
      modifiersText.splitToSequence('+').forEach { result.add(it.trim()) }
    }
    else {
      modifiersText.toCharArray().forEach {
        result.add(it.toString())
      }
    }
  }

  private fun createPresentation(textColor: JBColor, fillColor: Color?, borderColorKey: String, font: Font): ShortcutTextPresentation {
    return ShortcutTextPresentation(textColor, fillColor, getColor(borderColorKey), font)
  }

  private fun createTextElement(
    text: String,
    textGap: Int,
    c: JComponent,
    font: Font,
    presentation: ShortcutTextPresentation,
  ): ShortcutTextElement {
    val fontMetrics = c.getFontMetrics(font)
    val textWidth = fontMetrics.stringWidth(text) + textGap * 2
    return ShortcutTextElement(textWidth, text, presentation)
  }

  private fun getColor(name: String): Color? {
    val color = UIManager.getColor(name)
    if (color is Color) {
      return color
    }
    return null
  }

  fun getWidth(): Int {
    return width
  }

  fun draw(bounds: Rectangle, g: Graphics2D) {
    var startX = bounds.x + bounds.width - width
    val y = bounds.y + (bounds.height - height) / 2

    for (element in elements) {
      when (element) {
        is GapElement -> {
          startX += element.width
        }

        is ShortcutTextElement -> {
          val presentation = element.presentation

          val fillColor = presentation.fillColor
          if (fillColor != null) {
            g.color = fillColor
            g.fillRoundRect(startX, y, element.width, height, round, round)
          }

          val borderColor = presentation.borderColor
          if (borderColor != null) {
            g.color = borderColor
            g.drawRoundRect(startX, y, element.width, height, round, round)
          }

          g.color = presentation.textColor
          g.font = presentation.font
          UIUtil.drawCenteredString(g, Rectangle(startX, y, element.width, height), element.text)

          startX += element.width
        }
      }
    }
  }
}

private val textSeparatorGap: Int
  get() = JBUI.scale(8)

private fun fillElements(
  elements: MutableList<BaseElement>,
  shortcutsBlocks: MutableList<List<BaseElement>>,
  maxWidth: Int,
  fontMetrics: FontMetrics,
  textSeparatorPresentation: ShortcutTextPresentation,
) {
  val flattenShortcuts = shortcutsBlocks.flatten()
  var currentWidth = flattenShortcuts.sumOf { it.width }
  if (maxWidth == -1 || currentWidth <= maxWidth) {
    elements.addAll(flattenShortcuts)
    return
  }

  currentWidth -= shortcutsBlocks.removeLast().sumOf { it.width }
  var moreCount = 1
  val textSeparatorGap = textSeparatorGap

  while (shortcutsBlocks.isNotEmpty()) {
    val orMoreText = KeyMapBundle.message("shortcuts.or.more", moreCount)
    val orMoreTextWidth = fontMetrics.stringWidth(orMoreText)
    if (currentWidth + textSeparatorGap + orMoreTextWidth <= maxWidth) {
      elements.addAll(shortcutsBlocks.flatten())
      elements.add(GapElement(textSeparatorGap))
      elements.add(ShortcutTextElement(orMoreTextWidth, orMoreText, textSeparatorPresentation))

      return
    }

    currentWidth -= shortcutsBlocks.removeLast().sumOf { it.width }
    moreCount++
  }

  val tinyInfoText = KeyMapBundle.message("shortcuts.tiny.info")
  val tinyInfoTextWidth = fontMetrics.stringWidth(tinyInfoText)

  if (tinyInfoTextWidth <= maxWidth) {
    elements.add(ShortcutTextElement(tinyInfoTextWidth, tinyInfoText, textSeparatorPresentation))
  }
}

private data class ShortcutTextPresentation(
  val textColor: Color,
  val fillColor: Color?,
  val borderColor: Color?,
  val font: Font,
)

private data class ShortcutTextElement(
  override val width: Int,
  val text: String,
  val presentation: ShortcutTextPresentation,
) : BaseElement

private data class GapElement(
  override val width: Int,
) : BaseElement

private sealed interface BaseElement {
  val width: Int
}