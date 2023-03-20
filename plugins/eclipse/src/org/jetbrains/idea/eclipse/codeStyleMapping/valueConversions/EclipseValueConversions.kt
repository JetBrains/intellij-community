// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions.*

internal object InsertConvertor : Convertor<String, Boolean> {
  override fun convertOutgoing(value: Boolean): String = if (value) VALUE_INSERT else VALUE_DO_NOT_INSERT
  override fun convertIncoming(value: String): Boolean = when (value.lowercase()) {
    VALUE_INSERT -> true
    VALUE_DO_NOT_INSERT -> false
    else -> throw UnexpectedIncomingValue(value)
  }
}

internal fun SettingMapping<Boolean>.convertInsert() = convert(InsertConvertor)

internal object BlankLinesConvertor : Convertor<String, Int> {
  override fun convertIncoming(value: String): Int = try {
    /*
    '-' prefix indicates that Eclipse should enforce the specified number of blank lines,
    rather than preserving up to the allowed maximum in "number_of_empty_lines_to_preserve".
    IDEA doesn't offer such option, so we ignore it.
     */
    value.removePrefix("-").toInt()
  }
  catch (e: NumberFormatException) {
    throw UnexpectedIncomingValue(value)
  }

  override fun convertOutgoing(value: Int): String = value.coerceIn(0, 99).toString()
}

internal fun SettingMapping<Int>.convertBlankLines() = convert(BlankLinesConvertor)

/**
 * Unlike Eclipse, IDEA does not have settings to enforce this behaviour.
 */
internal object BracedCodeOnOneLineConvertor : Convertor<String, Boolean> {
  override fun convertIncoming(value: String): Boolean = when (value.lowercase()) {
    BRACES_ONE_LINE_NEVER -> false
    BRACES_ONE_LINE_IF_EMPTY,
    BRACES_ONE_LINE_IF_IN_WIDTH_LIMIT,
    BRACES_ONE_LINE_IF_SINGLE_ITEM,
    BRACES_ONE_LINE_PRESERVE_STATE -> true
    else -> throw UnexpectedIncomingValue(value)
  }

  override fun convertOutgoing(value: Boolean): String {
    if (value) {
      return BRACES_ONE_LINE_PRESERVE_STATE
    }
    else {
      return BRACES_ONE_LINE_NEVER
    }
  }
}

internal fun SettingMapping<Boolean>.convertBracedCodeOnOneLine() = convert(BracedCodeOnOneLineConvertor)

internal object BracePositionConvertor : Convertor<String, Int> {
  override fun convertIncoming(value: String): Int = when (value.lowercase()) {
    VALUE_END_OF_LINE -> CommonCodeStyleSettings.END_OF_LINE
    VALUE_NEXT_LINE -> CommonCodeStyleSettings.NEXT_LINE
    VALUE_NEXT_LINE_SHIFTED -> CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    VALUE_NEXT_LINE_IF_WRAPPED -> CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    else -> throw UnexpectedIncomingValue(value)
  }

  override fun convertOutgoing(value: Int): String = when (value) {
    CommonCodeStyleSettings.END_OF_LINE -> VALUE_END_OF_LINE
    CommonCodeStyleSettings.NEXT_LINE -> VALUE_NEXT_LINE
    CommonCodeStyleSettings.NEXT_LINE_SHIFTED,
    CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 -> VALUE_NEXT_LINE_SHIFTED
    CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED -> VALUE_NEXT_LINE_IF_WRAPPED
    else -> VALUE_END_OF_LINE
  }
}

internal fun SettingMapping<Int>.convertBracePosition() = convert(BracePositionConvertor)

internal class ParenPositionConvertorFactory(private val keepLineBreaks: Boolean) {
  internal class ParenPositionConvertor(private val keepLineBreaks: Boolean,
                                        private val eclipseValueToExportIfTrue: String) : Convertor<String, Boolean> {
    override fun convertIncoming(value: String): Boolean = when (value.lowercase()) {
      PARENS_COMMON_LINES -> false
      PARENS_SEPARATE_LINES,
      PARENS_SEPARATE_LINES_IF_NOT_EMPTY,
      PARENS_SEPARATE_LINES_IF_WRAPPED,
      PARENS_PRESERVE_POSITIONS -> true
      else -> throw UnexpectedIncomingValue(value)
    }

    override fun convertOutgoing(value: Boolean): String =
      if (value) eclipseValueToExportIfTrue
      else if (keepLineBreaks) PARENS_PRESERVE_POSITIONS
      else PARENS_COMMON_LINES
  }

  fun ifInternalIsTrueExport(eclipseValue: String) = ParenPositionConvertor(keepLineBreaks, eclipseValue)
}

internal object TabCharacterConvertor : Convertor<String, String> {
  override fun convertIncoming(value: String): String {
    return when (val lowercase = value.lowercase()) {
      TAB_CHAR_MIXED,
      TAB_CHAR_TAB,
      TAB_CHAR_SPACE -> lowercase
      else -> throw UnexpectedIncomingValue(value)
    }
  }

  override fun convertOutgoing(value: String): String = value
}
