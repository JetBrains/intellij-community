// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.SoftWrap
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.TextChangeImpl

/* Custom wraps piggyback on soft wraps.
 * They are inserted into soft wrap linear storage and kept up to date alongside soft-wraps.
 * The complicated logic of splitting a logical line into multiple visual lines
 * in coordinate-mapping and painting is thus shared between custom and soft wraps. */
internal class CustomWrapToSoftWrapAdapter(
  val customWrap: CustomWrap,
  val type: Type,
  val editor: EditorImpl,
) : SoftWrapEx {
  private val change: TextChangeImpl = TextChangeImpl(
    "\n" + " ".repeat(maxOf(0, indentInColumns - 1)),
    customWrap.offset
  )

  override fun advance(diff: Int) {
    assert(type != Type.PASS_THROUGH) { "Pass-through custom wrap adapters should not be manually advanced" }
    val start = change.start
    if (customWrap.offset - start != diff) {
      thisLogger().error("Unexpected diff: $diff vs actual diff: ${customWrap.offset - start}")
    }
    change.start += diff
    change.end = change.start
    return
  }

  override val isPaintable: Boolean
    get() = false

  override fun getStart(): Int = when (type) {
    Type.PASS_THROUGH -> customWrap.offset
    Type.DEFAULT -> change.start
  }

  override fun getEnd(): Int = when (type) {
    Type.PASS_THROUGH -> customWrap.offset
    Type.DEFAULT -> change.end
  }

  override fun getText(): CharSequence = change.text

  override fun getChars(): CharArray = change.chars

  override fun getIndentInColumns(): Int = customWrap.indent

  override fun getIndentInPixels(): Int = EditorUtil.getPlainSpaceWidth(editor) * indentInColumns

  enum class Type {
    DEFAULT,
    PASS_THROUGH
  }

  override fun toString(): String = "CustomWrapAdapter[type=$type, start=$start, customWrap=$customWrap]"
}
