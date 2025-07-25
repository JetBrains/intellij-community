// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.process.AnsiStreamingLexer

// Removes ESC chars
internal class CleanBuffer(private val junkToDrop: Char? = null) {
  private companion object {
    val NEW_LINES_EMPTY_CHARS = Regex("(\r|\n| |\t|\\s|\\p{Z})")
    val OS_COMMAND_SET_TITLE = Regex("\\x1b]0;[^\\x1b\\x07]+(:?\\x9C|\\x07|\\x1b\\x5c)")
  }

  private val lexer = AnsiStreamingLexer()
  private val buffer = StringBuilder()
  fun add(line: String) {
    lexer.append(line)
    while (true) {
      var s = lexer.nextText
      if (s == null) {
        break
      }
      else {
        if (junkToDrop != null) {
          s = s.replace(junkToDrop, ' ')
        }
        val str = s.replace(NEW_LINES_EMPTY_CHARS, "")
        buffer.append(str)
        val title = OS_COMMAND_SET_TITLE.find(buffer)
        if (title != null) {
          buffer.deleteRange(title.range.start, title.range.last + 1)
        }
      }
    }
  }

  fun getString(): String = buffer.toString()
  fun setPosEnd(needle: String) {
    val pos = buffer.indexOf(needle)
    assert(pos > 0) { "Can't find $needle in $buffer" }
    val newValue = buffer.removeRange(0, pos + needle.length)
    buffer.clear()
    buffer.append(newValue)
  }
}