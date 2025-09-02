// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.diagnostic.logging.DefaultLogFormatter
import com.intellij.diagnostic.logging.LogFilterModel
import com.intellij.execution.process.ProcessOutputType
import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Format event log records in the Statistics Event Log toolwindow.
 * Paint event's fields in magenta, their values in green if the log passed the validation rules.
 * Use ANSI escape codes for setting RGB text color.
 * Paint a log message in red color if it didn't pass the validation rules.
 * Log messages in a multiline log presentation if the user chooses this format.
 * Otherwise, log them in a one-line log presentation.
 */
class StatisticsEventLogFormatter(private val model: LogFilterModel) : DefaultLogFormatter() {
  private var isMultilineLog: Boolean = false
  private lateinit var colorMagenta: Color
  private lateinit var colorGreen: Color

  override fun formatMessage(msg: String?): String? {
    if (msg == null) return null
    val processingResult = model.processLine(msg)
    val isStderr = processingResult.key == ProcessOutputType.STDERR
    colorMagenta = getColorForCurrentTheme(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY, JBColor.MAGENTA)
    colorGreen = getColorForCurrentTheme(JsonSyntaxHighlighterFactory.JSON_STRING, JBColor.GREEN)

    val eventDataOffset = msg.indexOf('{')
    val fieldValueList = parseEventData(msg.substring(eventDataOffset))
    val formatedEventData = if (isMultilineLog) formatEventDataToMultiline(fieldValueList, isStderr)
    else formatEventDataToOneLine(fieldValueList, isStderr)
    return msg.substring(0, eventDataOffset).trim() + formatedEventData
  }

  fun updateLogPresentation(isMultilineLog: Boolean) {
    this.isMultilineLog = isMultilineLog
  }

  private fun formatEventDataToOneLine(fieldValueList: List<Pair<String, String>>, isStderr: Boolean): String {
    val formatedEventData = StringBuilder()
    formatedEventData.append(" {")
    var isFirstPair = true
    fieldValueList.forEach {
      if (!isFirstPair) formatedEventData.append(", ")
      paintFieldValue(isStderr, it, formatedEventData)
      isFirstPair = false
    }
    formatedEventData.append("}")
    return formatedEventData.toString()
  }

  private fun formatEventDataToMultiline(fieldValueList: List<Pair<String, String>>, isStderr: Boolean): String {
    val formatedEventData = StringBuilder()
    formatedEventData.append("\n{")
    if (!fieldValueList.isEmpty()) formatedEventData.append("\n\t")
    var isFirstPair = true
    fieldValueList.forEach {
      if (!isFirstPair) formatedEventData.append(",\n\t")
      paintFieldValue(isStderr, it, formatedEventData)
      isFirstPair = false
    }
    formatedEventData.append("\n}")
    return formatedEventData.toString()
  }

  private fun parseEventData(eventData: String): List<Pair<String, String>> {
    val fieldValueList = mutableListOf<Pair<String, String>>()
    val parser: JsonParser = JsonFactory().createParser(eventData)
    var startEventDataOffset = 0
    var endEventDataOffset: Int
    var isFieldValueInProgress = false
    var bracketsCount = 0
    while (parser.nextToken() != null) {
      val token = parser.currentToken
      when (token) {
        JsonToken.START_OBJECT -> {
          if (isFieldValueInProgress) {
            bracketsCount++
            continue
          }
        }

        JsonToken.END_OBJECT -> {
          if (bracketsCount > 0) {
            bracketsCount--
            continue
          }
          if (startEventDataOffset != 0) {
            endEventDataOffset = parser.currentTokenLocation().charOffset.toInt()
            val fieldValuePair = parseFieldValuePair(eventData.substring(startEventDataOffset, endEventDataOffset))
            fieldValueList.add(fieldValuePair)
          }
          isFieldValueInProgress = false
        }

        JsonToken.FIELD_NAME -> {
          isFieldValueInProgress = true
          if (bracketsCount > 0) continue
          if (startEventDataOffset != 0) {
            endEventDataOffset = parser.currentTokenLocation().charOffset.toInt()
            val fieldValuePair = parseFieldValuePair(eventData.substring(startEventDataOffset, endEventDataOffset - 1).trim())
            fieldValueList.add(fieldValuePair)
          }
          startEventDataOffset = parser.currentTokenLocation().charOffset.toInt()
        }
        else -> {
          continue
        }
      }
    }
    return fieldValueList
  }

  private fun parseFieldValuePair(fieldValuePair: String): Pair<String, String> {
    val valueOffset = fieldValuePair.indexOf(':')
    val field = fieldValuePair.substring(0, valueOffset)
    val value = fieldValuePair.substring(valueOffset + 1).trimEnd(',')
    return Pair(field, value)
  }

  private fun paintFieldValue(isStderr: Boolean, fieldValue: Pair<String, String>, formatedEventData: StringBuilder) {
    val formatedField = if (isStderr) fieldValue.first else paintInMagenta(fieldValue.first)
    formatedEventData.append(formatedField)
    formatedEventData.append(":")
    val formatedValue = if (isStderr) fieldValue.second else paintInGreen(fieldValue.second)
    formatedEventData.append(formatedValue)
  }

  private fun paintInMagenta(field: String): String = "\u001B[38;2;${colorMagenta.red};${colorMagenta.green};${colorMagenta.blue}m$field\u001B[0m"

  private fun paintInGreen(value: String): String = "\u001B[38;2;${colorGreen.red};${colorGreen.green};${colorGreen.blue}m$value\u001B[0m"

  private fun getColorForCurrentTheme(textAttributesKey: TextAttributesKey, defaultColor: Color): Color {
    val colorScheme = EditorColorsManager.getInstance().globalScheme
    val attribute = colorScheme.getAttributes(textAttributesKey)
    return if (attribute != null) attribute.foregroundColor else defaultColor
  }
}