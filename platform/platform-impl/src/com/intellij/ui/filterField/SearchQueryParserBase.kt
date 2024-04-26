package com.intellij.ui.filterField

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings

abstract class SearchQueryParserBase {
  private var words: Set<String> = emptySet()

  fun getWords(): Set<String> = words

  protected fun addWords(word: String) {
    words += word
  }

  protected abstract fun handleAttribute(name: String, value: String)

  protected abstract fun getAttributes(): Set<String>

  private fun splitQuery(query: String): List<String> {
    val parts = mutableListOf<String>()

    val length = query.length
    var index = 0

    while (index < length) {
      val startCh = query[index++]
      if (startCh == ' ') {
        continue
      }
      if (startCh == ',') {
        parts.add(",")
        continue
      }
      if (startCh == '"') {
        val end = query.indexOf('"', index)
        if (end == -1) {
          break
        }
        parts.add(query.substring(index, end))
        index = end + 1
        continue
      }
      val start = index - 1
      while (index <= length) {
        if (index == length) {
          parts.add(query.substring(start))
          break
        }
        val nextCh = query[index++]
        if (nextCh == ':' || nextCh == ' ' || nextCh == ',' || index == length) {
          parts.add(query.substring(start, if (nextCh == ' ' || nextCh == ',') index - 1 else index))
          if (nextCh == ',') {
            parts.add(",")
          }
          break
        }
      }
    }

    return parts
  }

  fun parse(query: String) {
    val parts = splitQuery(query)
    val size = parts.size

    if (size == 0) return

    var index = 0
    while (index < size) {
      val nameIndex = index
      val name = parts[index++]

      if (getAttributes().contains(name)) {
        if (index < size) {
          while (index < size) {
            if (parts[index] != "," && parts[index - 1] != "," && index - 1 != nameIndex) {
              break
            }

            if (parts[index] != "," && (parts[index - 1] == "," || index - 1 == nameIndex)) {
              handleAttribute(name, parts[index])
            }

            index++
          }
        }
        else {
          addWords(name)
          break
        }
      }
      else {
        addWords(name)
      }
    }
  }

  private enum class QueryCompletionParserState {
    TEXT, EMPTY_SPACE, ESCAPED_VALUE, ATTRIBUTE_NAME
  }

  class QueryCompletionPosition(
    val attributeName: String,
    val attributeValue: String?,
    val startPosition: Int
  )

  companion object {
    @JvmStatic
    fun wrapAttribute(value: String): String {
      return if (StringUtil.containsAnyChar(value, " ,:")) StringUtil.wrapWithDoubleQuote(value) else value
    }

    @JvmStatic
    fun parseAttributeInQuery(query: String, completionPosition: Int): QueryCompletionPosition {
      var end = completionPosition
      var index = end - 1
      var value: String? = null
      var state = QueryCompletionParserState.TEXT

      var startPosition: Int? = null

      loop@ while (index >= 0) {
        val ch = query[index]

        when (state) {
          QueryCompletionParserState.ATTRIBUTE_NAME -> {
            end = index + 1
            index--
            while (index >= 0) {
              if (query[index] == ' ' || query[index] == ',' || query[index] == '\"') {
                break
              }
              index--
            }
            break@loop
          }
          QueryCompletionParserState.ESCAPED_VALUE -> {
            if (ch == '\"') {
              state = QueryCompletionParserState.TEXT
              if (value == null) {
                value = query.substring(index + 1, end)
                startPosition = index + 1
              }
            }
          }
          QueryCompletionParserState.EMPTY_SPACE -> {
            if (ch == ',') {
              state = QueryCompletionParserState.TEXT
            }
            else if (ch == ':') {
              state = QueryCompletionParserState.ATTRIBUTE_NAME
              if (value == null) {
                value = query.substring(index + 1, end)
                startPosition = index + 1
              }
              continue@loop // do not skip ':'
            }
            else if (ch != '\"' && ch != ' ') {
              if (startPosition == null) {
                startPosition = index + 1
              }
              return QueryCompletionPosition(StringUtil.trim(Strings.notNullize(value)), null, startPosition)
            }
          }
          else -> {
            if (ch == ',') {
              if (value == null) {
                value = query.substring(index + 1, end)
                startPosition = index + 1
              }
            }
            else if (ch == ':') {
              state = QueryCompletionParserState.ATTRIBUTE_NAME
              if (value == null) {
                value = query.substring(index + 1, end)
                startPosition = index + 1
              }
              continue@loop // do not skip ':'
            }
            else if (ch == '\"') {
              state = QueryCompletionParserState.ESCAPED_VALUE
            }
            else if (ch == ' ') {
              state = QueryCompletionParserState.EMPTY_SPACE
              if (value == null) {
                value = query.substring(index + 1, end)
                startPosition = index + 1
              }
            }
          }
        }
        index--
      }

      if (startPosition == null) {
        startPosition = index + 1
      }

      val attributeName = StringUtil.trim(query.substring(index + 1, end))
      if (state == QueryCompletionParserState.ATTRIBUTE_NAME) {
        // attribute name found
        return QueryCompletionPosition(attributeName, StringUtil.trim(value), startPosition)
      }
      // only attribute name present, no value
      return QueryCompletionPosition(attributeName, null, startPosition)
    }
  }
}