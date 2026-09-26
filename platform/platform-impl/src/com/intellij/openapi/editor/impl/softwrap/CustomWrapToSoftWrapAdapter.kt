// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.TextChangeImpl

/* Custom wraps piggyback on soft wraps.
 * They are inserted into soft wrap linear storage and kept up to date alongside soft-wraps.
 * The complicated logic of splitting a logical line into multiple visual lines
 * in coordinate-mapping and painting is thus shared between custom and soft wraps. */
internal class CustomWrapToSoftWrapAdapter(
  val customWrap: CustomWrap,
  val editor: EditorImpl,
) : SoftWrapEx {
  private val change: TextChangeImpl = TextChangeImpl(
    "\n" + " ".repeat(maxOf(0, indentInColumns - 1)),
    customWrap.offset
  )

  override fun advance(diff: Int) {
    val start = change.start
    if (customWrap.offset - start != diff) {
      LOG.error("Unexpected diff: $diff vs actual diff: ${customWrap.offset - start}")
    }
    change.start += diff
    change.end = change.start
    return
  }

  override fun getStart(): Int = change.start

  override fun getEnd(): Int = customWrap.offset

  override fun getText(): CharSequence = change.text

  override fun getChars(): CharArray = change.chars

  override fun getIndentInColumns(): Int = customWrap.indent

  override fun getIndentInPixels(): Int = EditorUtil.getPlainSpaceWidth(editor) * indentInColumns

  override fun isCustomSoftWrap(): Boolean = true

  override fun toString(): String = "CustomWrapAdapter[start=$start, customWrap=$customWrap]"
}

private val LOG = logger<CustomWrapToSoftWrapAdapter>()
