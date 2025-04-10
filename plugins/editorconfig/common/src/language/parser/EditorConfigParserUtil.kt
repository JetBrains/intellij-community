// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.util.Key
import org.editorconfig.language.psi.EditorConfigElementTypes

@Suppress("UNUSED_PARAMETER")
object EditorConfigParserUtil : GeneratedParserUtilBase() {
  @JvmStatic
  fun unbindComments(builder: PsiBuilder, level: Int): Boolean {
    val marker = builder.latestDoneMarker as? PsiBuilder.Marker
    marker?.setCustomEdgeTokenBinders(
      { tokens, _, _ -> tokens.size },
      { _, _, _ -> 0 }
    )
    return true
  }

  @JvmStatic
  fun rawOptionValue(builder: PsiBuilder, level: Int): Boolean {
    if (!recursion_guard_(builder, level, "specialOptionValue")) return false
    val marker = enter_section_(builder)
    while (!followedByNewLineOrEndOfFile(builder, level)) {
      builder.advanceLexer()
    }
    exit_section_(builder, marker, EditorConfigElementTypes.RAW_OPTION_VALUE, true)
    return true
  }

  @JvmStatic
  fun isOptionWithRawValueKeyAhead(builder: PsiBuilder, level: Int): Boolean =
    nextTokenIs(builder, EditorConfigElementTypes.IDENTIFIER) && builder.tokenText in specialKeys

  private val specialKeys = listOf("file_header_template",
                                   "ij_formatter_off_tag",
                                   "ij_formatter_on_tag")

  /**
   * Tests whether a new line has just been skipped
   */
  @JvmStatic
  fun followedByNewLineOrEndOfFile(builder: PsiBuilder, level: Int): Boolean {
    if (builder.eof()) return true
    val currentTokenStart = builder.rawTokenTypeStart(0)
    val data = builder.getUserData(KEY) ?: return false
    if (currentTokenStart != data.end) {
      // This means that no whitespaces have been skipped recently,
      // so we now know for sure that there are no newlines between
      return false
    }
    val whitespaceText = builder.originalText.subSequence(data.start, data.end)
    return whitespaceText.contains('\n')
  }

  val KEY: Key<EditorConfigSkippedWhitespaceData> = Key<EditorConfigSkippedWhitespaceData>("EditorConfigSkippedWhitespaceData")
}

