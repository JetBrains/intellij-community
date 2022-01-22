// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions.*

internal class EclipseWrapValue(var lineWrapPolicy: LineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP,
                                var indentationPolicy: IndentationPolicy = IndentationPolicy.DEFAULT_INDENTATION,
                                var isForceSplit: Boolean = false) {
  var isAligned
    get() = indentationPolicy == IndentationPolicy.INDENT_ON_COLUMN
    set(value) {
      indentationPolicy = if (value) IndentationPolicy.INDENT_ON_COLUMN else IndentationPolicy.DEFAULT_INDENTATION
    }

  fun encode(): Int {
    return (if (isForceSplit) 0x01 else 0x00) or indentationPolicy.bits or lineWrapPolicy.bits
  }

  companion object {
    /**
     * @throws [UnexpectedIncomingValue] if any of the bit combinations is unknown
     */
    fun decode(encoded: Int): EclipseWrapValue {
      /* bit idx:  7 6 5 4   3 2 1 0
         encoded:  0 0 1 0   0 0 1 1   -- encodes WRAP_FIRST_OTHERS_WHERE_NECESSARY, INDENT_ON_COLUMN and force split
                   ?|_____|  ?|___|_|
                      |         |  > force split
                      |         > indentation policy
                      > line wrap policy
      */
      val isForceSplit = encoded and FORCE_SPLIT_MASK == 1

      val indentationPolicy = when (encoded and INDENT_POLICY_MASK) {
        IndentationPolicy.DEFAULT_INDENTATION.bits -> IndentationPolicy.DEFAULT_INDENTATION
        IndentationPolicy.INDENT_ON_COLUMN.bits -> IndentationPolicy.INDENT_ON_COLUMN
        IndentationPolicy.INDENT_BY_ONE.bits -> IndentationPolicy.INDENT_BY_ONE
        else -> throw UnexpectedIncomingValue(encoded)
      }

      val lineWrapPolicy = when (encoded and LINE_WRAP_POLICY_MASK) {
        LineWrapPolicy.DO_NOT_WRAP.bits -> LineWrapPolicy.DO_NOT_WRAP
        LineWrapPolicy.WRAP_WHERE_NECESSARY.bits -> LineWrapPolicy.WRAP_WHERE_NECESSARY
        LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY.bits -> LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY
        LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH.bits -> LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
        LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST.bits -> LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST
        LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST.bits -> LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
        else -> throw UnexpectedIncomingValue(encoded)
      }
      return EclipseWrapValue(lineWrapPolicy, indentationPolicy, isForceSplit)
    }

    fun doNotWrap() = EclipseWrapValue()
  }

  override fun toString(): String = "(${encode()}: $lineWrapPolicy, $indentationPolicy, $isForceSplit)"
}

internal object WrapConvertor : Convertor<String, EclipseWrapValue> {
  override fun convertOutgoing(value: EclipseWrapValue): String =
    IntConvertor.convertOutgoing(value.encode())

  override fun convertIncoming(value: String): EclipseWrapValue =
    EclipseWrapValue.decode(IntConvertor.convertIncoming(value))
}

internal fun SettingMapping<EclipseWrapValue>.convertWrap() = convert(WrapConvertor)

internal fun listInBracketsWrap(wrap: SettingMapping<Int>,
                                alignWhenMultiline: SettingMapping<Boolean>,
                                newLineAfterLeftBracket: SettingMapping<Boolean>) =
  ListInBracketsWrapSettingMapping(wrap, alignWhenMultiline, newLineAfterLeftBracket)
    .convert(WrapConvertor)

internal class ListInBracketsWrapSettingMapping(
  private val wrap: SettingMapping<Int>,
  private val alignWhenMultiline: SettingMapping<Boolean>,
  private val newLineAfterLeftBracket: SettingMapping<Boolean>
) : SettingMapping<EclipseWrapValue> {

  init {
    if (!wrap.isExportAllowed || !alignWhenMultiline.isExportAllowed || !newLineAfterLeftBracket.isExportAllowed) {
      throw IllegalArgumentException("Export must be allowed for all subfields")
    }
  }

  override fun export() = EclipseWrapValue().apply {
    isAligned = alignWhenMultiline.export() && !newLineAfterLeftBracket.export()
    when (wrap.export()) {
      CommonCodeStyleSettings.DO_NOT_WRAP -> {
        lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
      }
      CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
        lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
      }
      CommonCodeStyleSettings.WRAP_ALWAYS -> {
        lineWrapPolicy = LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
        isForceSplit = true
      }
      else /* chop down if long */ -> {
        lineWrapPolicy = LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
      }
    }
  }

  override fun import(value: EclipseWrapValue) {
    alignWhenMultiline.importIfAllowed(value.isAligned)
    when (value.lineWrapPolicy) {
      LineWrapPolicy.DO_NOT_WRAP -> {
        wrap.importIfAllowed(CommonCodeStyleSettings.DO_NOT_WRAP)
      }
      LineWrapPolicy.WRAP_WHERE_NECESSARY -> {
        if (value.isForceSplit) {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
          newLineAfterLeftBracket.importIfAllowed(true)
        }
        else {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
        }
      }
      LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
        newLineAfterLeftBracket.importIfAllowed(true)
        wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
      }
      LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
      LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST -> {
        newLineAfterLeftBracket.importIfAllowed(true)
        if (value.isForceSplit) {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
        }
        else {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
        }
      }
      LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
        if (value.isForceSplit) {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
        }
        else {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
        }
      }
    }
  }
}

internal fun annotationWrap(wrap: SettingMapping<Int>) =
  ListWrapSettingMapping(wrap, const(false))
    .convert(WrapConvertor)

internal fun listWrap(wrap: SettingMapping<Int>,
                      alignWhenMultiline: SettingMapping<Boolean>) =
  ListWrapSettingMapping(wrap, alignWhenMultiline)
    .convert(WrapConvertor)

internal class ListWrapSettingMapping(
  private val wrap: SettingMapping<Int>,
  private val alignWhenMultiline: SettingMapping<Boolean>
) : SettingMapping<EclipseWrapValue> {
  init {
    if (!wrap.isExportAllowed) {
      throw IllegalArgumentException("Export must be allowed for all subfields")
    }
  }

  override fun export() = EclipseWrapValue().apply {
    isAligned = alignWhenMultiline.export()
    when (wrap.export()) {
      CommonCodeStyleSettings.DO_NOT_WRAP -> {
        lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
      }
      CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
        lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
      }
      CommonCodeStyleSettings.WRAP_ALWAYS -> {
        lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
        isForceSplit = true
      }
      else /* chop down if long */ -> {
        lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
      }
    }
  }

  override fun import(value: EclipseWrapValue) {
    alignWhenMultiline.importIfAllowed(value.isAligned)
    when (value.lineWrapPolicy) {
      LineWrapPolicy.DO_NOT_WRAP -> {
        wrap.importIfAllowed(CommonCodeStyleSettings.DO_NOT_WRAP)
      }
      LineWrapPolicy.WRAP_WHERE_NECESSARY -> {
        if (value.isForceSplit) {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
        }
        else {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
        }
      }
      LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
        wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
      }
      LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
      LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST,
      LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
        if (value.isForceSplit) {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
        }
        else {
          wrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
        }
      }
    }
  }
}

internal fun keywordFollowedByListWrap(keywordWrap: SettingMapping<Int>,
                                       listWrap: SettingMapping<Int>,
                                       alignWhenMultiline: SettingMapping<Boolean>) =
  KeywordFollowedByListWrapSettingMapping(keywordWrap, listWrap, alignWhenMultiline)
    .convert(WrapConvertor)

internal class KeywordFollowedByListWrapSettingMapping(
  private val keywordWrap: SettingMapping<Int>,
  private val listWrap: SettingMapping<Int>,
  private val alignWhenMultiline: SettingMapping<Boolean>)
  : SettingMapping<EclipseWrapValue> {
  init {
    if (!keywordWrap.isExportAllowed || !listWrap.isExportAllowed || !alignWhenMultiline.isExportAllowed) {
      throw IllegalArgumentException("Export must be allowed for all subfields")
    }
  }

  override fun export() = EclipseWrapValue().apply {
    isAligned = alignWhenMultiline.export()
    when (listWrap.export()) {
      CommonCodeStyleSettings.DO_NOT_WRAP -> {
        // extend keyword might be wrapped here, but this cannot be captured in Eclipse
        lineWrapPolicy = LineWrapPolicy.DO_NOT_WRAP
      }
      CommonCodeStyleSettings.WRAP_AS_NEEDED -> {
        when (keywordWrap.export()) {
          CommonCodeStyleSettings.WRAP_ALWAYS -> {
            lineWrapPolicy = LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY
            isForceSplit = true
          }
          else -> lineWrapPolicy = LineWrapPolicy.WRAP_WHERE_NECESSARY
        }
      }
      CommonCodeStyleSettings.WRAP_ALWAYS -> {
        when (keywordWrap.export()) {
          CommonCodeStyleSettings.WRAP_ALWAYS -> {
            lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
          }
          else -> {
            lineWrapPolicy = LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
          }
        }
        isForceSplit = true
      }
      else /* chop down if long */ -> {
        when (keywordWrap.export()) {
          CommonCodeStyleSettings.WRAP_ALWAYS -> {
            lineWrapPolicy = LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH
          }
          else -> {
            lineWrapPolicy = LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST
          }
        }
      }
    }
  }

  override fun import(value: EclipseWrapValue) {
    alignWhenMultiline.importIfAllowed(value.isAligned)
    when (value.lineWrapPolicy) {
      LineWrapPolicy.DO_NOT_WRAP -> {
        keywordWrap.importIfAllowed(CommonCodeStyleSettings.DO_NOT_WRAP)
        listWrap.importIfAllowed(CommonCodeStyleSettings.DO_NOT_WRAP)
      }
      LineWrapPolicy.WRAP_WHERE_NECESSARY -> {
        if (value.isForceSplit) {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
        }
        else {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
        }
      }
      LineWrapPolicy.WRAP_FIRST_OTHERS_WHERE_NECESSARY -> {
        if (value.isForceSplit) {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
        }
        else {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
        }
      }
      LineWrapPolicy.WRAP_ALL_ON_NEW_LINE_EACH,
      LineWrapPolicy.WRAP_ALL_INDENT_EXCEPT_FIRST -> {
        if (value.isForceSplit) {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
        }
        else {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
        }
      }
      LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST -> {
        if (value.isForceSplit) {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_ALWAYS)
        }
        else {
          keywordWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED)
          listWrap.importIfAllowed(CommonCodeStyleSettings.WRAP_AS_NEEDED or CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
        }
      }
    }
  }
}
