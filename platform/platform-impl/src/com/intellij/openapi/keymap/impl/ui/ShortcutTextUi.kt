// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private val elements = mutableListOf<ShortcutTextElement>()

  constructor(shortcuts: Array<Shortcut>?, abbreviations: Collection<String>?, tree: JComponent, g: Graphics2D) {
    if ((shortcuts == null || shortcuts.isEmpty()) && (abbreviations == null || abbreviations.isEmpty())) {
      width = 0
      return
    }

    val fixedWidth = JBUI.scale(20)
    val textGap = JBUI.scale(4)
    val betweenGap = JBUI.scale(3)
    val separatorGap = JBUI.scale(8)

    val font = JBFont.medium()
    val boldFont = font.asBold()
    val boldMetrics = tree.getFontMetrics(boldFont)

    val shortcutPresentation = createPresentation(JBColor.namedColor("Shortcut.foreground", JBColor(0x0, 0xDFE1E5)),
                                                  "Shortcut.background", "Shortcut.borderColor", boldFont)

    val abbreviationPresentation = createPresentation(JBColor.namedColor("Abbreviation.foreground", JBColor(0x5A5D6B2E, 0xB4B8BF40.toInt())),
                                                      "Abbreviation.background", "Abbreviation.borderColor", boldFont)

    val separatorText = KeyMapBundle.message("or.separator")
    val separatorPresentation = ShortcutTextPresentation(JBUI.CurrentTheme.Label.foreground(), null, null, font)
    val separatorElement = createTextElement(separatorText, 0, separatorGap, tree.getFontMetrics(font), separatorPresentation, g)

    if (shortcuts != null && shortcuts.isNotEmpty()) {
      val lastShortcut = shortcuts.lastIndex

      for ((index, shortcut) in shortcuts.withIndex()) {
        val texts = getShortcutTexts(shortcut)
        val lastText = texts.lastIndex

        for ((index, text) in texts.withIndex()) {
          val gap = if (index == lastText) 0 else betweenGap

          if (text.length == 1) {
            elements.add(ShortcutTextElement(fixedWidth, gap, text, shortcutPresentation))
          }
          else {
            elements.add(createTextElement(text, gap, textGap, boldMetrics, shortcutPresentation, g))
          }
        }

        if (index != lastShortcut) {
          elements.add(separatorElement.copy())
        }
      }
    }

    if (abbreviations != null && abbreviations.isNotEmpty()) {
      if (elements.isNotEmpty()) {
        elements.add(separatorElement.copy())
      }

      val lastAbbreviation = abbreviations.size - 1

      for ((index, abbreviation) in abbreviations.withIndex()) {
        elements.add(createTextElement(abbreviation, 0, textGap, boldMetrics, abbreviationPresentation, g))

        if (index != lastAbbreviation) {
          elements.add(separatorElement.copy())
        }
      }
    }

    width = elements.sumOf { element -> element.width + element.gap } + JBUI.scale(10)
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
    if (KeymapUtil.isSimplifiedMacShortcuts() || !ClientSystemInfo.isMac()) {
      modifiersText.split("+").forEach { result.add(it.trim()) }
    }
    else {
      modifiersText.toCharArray().forEach {
        result.add(it.toString())
      }
    }
  }

  private fun createPresentation(textColor: Color, fillColorKey: String, borderColorKey: String, font: Font): ShortcutTextPresentation {
    return ShortcutTextPresentation(textColor, getColor(fillColorKey), getColor(borderColorKey), font)
  }

  private fun createTextElement(text: String, gap: Int, textGap: Int, fontMetrics: FontMetrics, presentation: ShortcutTextPresentation, g: Graphics2D): ShortcutTextElement {
    val bounds = fontMetrics.getStringBounds(text, g)
    val textWidth = bounds.width + textGap * 2
    return ShortcutTextElement(textWidth.toInt(), gap, text, presentation)
  }

  private fun getColor(name: String): Color? {
    val color = UIManager.getColor(name)
    if (color is Color) {
      return color
    }
    return null
  }

  fun draw(bounds: Rectangle, g: Graphics2D) {
    var startX = bounds.x + bounds.width - width
    val y = bounds.y + (bounds.height - height) / 2

    for (element in elements) {
      val presentation = element.presentation
      val borderColor = presentation.borderColor
      if (borderColor != null) {
        g.color = borderColor
        g.drawRoundRect(startX, y, element.width, height, round, round)
      }

      val fillColor = presentation.fillColor
      if (fillColor != null) {
        g.color = fillColor
        g.fillRoundRect(startX, y, element.width, height, round, round)
      }

      g.color = presentation.textColor
      g.font = presentation.font
      UIUtil.drawCenteredString(g, Rectangle(startX, y, element.width, height), element.text)

      startX += element.width + element.gap
    }
  }
}

private data class ShortcutTextPresentation(
  val textColor: Color,
  val fillColor: Color?,
  val borderColor: Color?,
  val font: Font,
)

private data class ShortcutTextElement(
  val width: Int,
  val gap: Int,
  val text: String,
  val presentation: ShortcutTextPresentation,
)