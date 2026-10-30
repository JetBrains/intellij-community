// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.platform.vcs.impl.shared.rpc.StructuralHunkDto
import com.intellij.platform.vcs.impl.shared.rpc.StructuralHunkPartDto
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

/**
 * The row of a hunk in a tree: the changed elements in the colors of their change, the count of the elements that do not fit, and the
 * detail. Every tree that shows hunks renders them with it, so that a hunk looks the same in each tree.
 */
@ApiStatus.Internal
object StructuralHunkPresentation {
  private const val MAX_ROW_LENGTH = 80
  private const val MAX_TOOLTIP_PARTS = 20
  private const val SEPARATOR = ", "

  /** Appends the row of [hunk] to [component], and sets the icon and the tooltip of the component. */
  fun render(component: SimpleColoredComponent, hunk: StructuralHunkDto) {
    component.icon = icon(hunk)
    for ((text, attributes) in fragments(hunk)) component.append(text, attributes)
    component.setToolTipText(tooltip(hunk))
  }

  /** The icon of the row. A line of text names a hunk without an element icon, so the row shows the text icon instead of an empty gap. */
  fun icon(hunk: StructuralHunkDto): Icon = hunk.icon?.icon() ?: AllIcons.FileTypes.Text

  /**
   * The pieces of the row. The row shows the parts that fit [MAX_ROW_LENGTH] characters, and at least one. A hunk can change hundreds of
   * elements, as a rename of many methods does, so the row gives the count of the other parts.
   */
  fun fragments(hunk: StructuralHunkDto): List<StructuralHunkFragment> {
    val parts = hunk.parts
    if (parts.isEmpty()) return listOf(grayed(VcsBundle.message("changes.structural.blank.lines")))
    val fragments = ArrayList<StructuralHunkFragment>()
    val shown = shownPartCount(parts)
    parts.take(shown).forEachIndexed { index, part ->
      if (index > 0) fragments += grayed(SEPARATOR)
      part.previousText?.let {
        fragments += StructuralHunkFragment(it, attributes(FileStatus.DELETED))
        fragments += grayed(" → ")
      }
      fragments += StructuralHunkFragment(part.text, attributes(part.fileStatus))
    }
    if (shown < parts.size) fragments += grayed(" " + VcsBundle.message("changes.structural.more.parts", parts.size - shown))
    if (hunk.isWhitespaceOnly) fragments += grayed("  " + VcsBundle.message("changes.structural.whitespace.only"))
    hunk.detail?.let {
      fragments += StructuralHunkFragment("  " + it.text, attributes(it.fileStatus, SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor))
    }
    return fragments
  }

  /** The text of the row without its colors and its detail. */
  fun text(hunk: StructuralHunkDto): @Nls String {
    val parts = hunk.parts
    if (parts.isEmpty()) return VcsBundle.message("changes.structural.blank.lines")
    val shown = shownPartCount(parts)
    val text = joinedParts(parts.take(shown))
    return if (shown < parts.size) text + " " + VcsBundle.message("changes.structural.more.parts", parts.size - shown) else text
  }

  /** Lists the parts one per line, at most [MAX_TOOLTIP_PARTS] of them, and then the detail. A hunk of blank lines has no tooltip. */
  fun tooltip(hunk: StructuralHunkDto): HtmlChunk? {
    val parts = hunk.parts
    if (parts.isEmpty()) return null
    val builder = HtmlBuilder()
    parts.take(MAX_TOOLTIP_PARTS).forEachIndexed { index, part ->
      if (index > 0) builder.br()
      builder.append(partText(part))
    }
    if (parts.size > MAX_TOOLTIP_PARTS) {
      builder.br().append(VcsBundle.message("changes.structural.more.parts", parts.size - MAX_TOOLTIP_PARTS))
    }
    hunk.detail?.let { builder.br().append(it.text) }
    return builder.wrapWithHtmlBody()
  }

  private fun shownPartCount(parts: List<StructuralHunkPartDto>): Int {
    var length = 0
    parts.forEachIndexed { index, part ->
      length += partText(part).length + if (index > 0) SEPARATOR.length else 0
      if (index > 0 && length > MAX_ROW_LENGTH) return index
    }
    return parts.size
  }

  /** The parts are source text, and the separators are symbols, so the text needs no translation. */
  private fun joinedParts(parts: List<StructuralHunkPartDto>): @NlsSafe String = parts.joinToString(SEPARATOR, transform = ::partText)

  private fun partText(part: StructuralHunkPartDto): @NlsSafe String = part.previousText?.let { "$it → ${part.text}" } ?: part.text

  private fun grayed(text: @Nls String): StructuralHunkFragment = StructuralHunkFragment(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)

  /** A deleted element has a line through it. */
  private fun attributes(status: FileStatus, color: Color? = status.color): SimpleTextAttributes {
    val style = if (status == FileStatus.DELETED) SimpleTextAttributes.STYLE_STRIKEOUT else SimpleTextAttributes.STYLE_PLAIN
    return SimpleTextAttributes(style, color)
  }
}

/** A piece of the row of a hunk, and its style. */
@ApiStatus.Internal
data class StructuralHunkFragment(val text: @Nls String, val attributes: SimpleTextAttributes)
