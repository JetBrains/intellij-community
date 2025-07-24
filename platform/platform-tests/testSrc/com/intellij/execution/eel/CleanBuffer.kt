// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.process.AnsiStreamingLexer

// Removes ESC chars
internal class CleanBuffer {
  private companion object {
    val NEW_LINES_EMPTY_CHARS = Regex("(\r?\n| |\t|\\s)")
    val OS_COMMAND_SET_TITLE = Regex("\\x1b]0;[^\\x1b\\x07]+(:?\\x9C|\\x07|\\x1b\\x5c)")
    val CLEAR_SCREEN = Regex("\\x1b\\[HJ")
  }

  private val lexer = AnsiStreamingLexer()
  private val buffer = StringBuilder()
  fun add(line: String) {
    lexer.append(line.replace(CLEAR_SCREEN, ""))
    while (true) {
      val s = lexer.nextText
      if (s == null) {
        break
      }
      else {
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

fun main() {
  val a = CleanBuffer()
  val c = 27.toChar()
  a.add("$c[?25h $c[HJ $c[8;1H $c[?25lhe   $c[?25h $c[?25lll   $c[?25h $c[HJ $c[8;5H $c[?25lo   $c[?25h $c[?25l $c[HJ $c[9;1H $c[?25h")
  a.add("$c[?25h $c[HJ $c[8;1H $c[?25lhe   $c[?25h $c[?25lll   $c[?25h $c[HJ $c[8;5H $c[?25lo   $c[?25h $c[?25l $c[HJ $c[9;1H $c[?25h")
  a.add("$c[?25h $c[HJ $c[8;1H $c[?25lhe   $c[?25h $c[?25lll   $c[?25h $c[HJ $c[8;5H $c[?25lo   $c[?25h $c[?25l $c[HJ $c[9;1H $c[?25h")
  a.add("$c[?25h $c[HJ $c[8;1H $c[?25lhe   $c[?25h $c[?25lll   $c[?25h $c[?25lo   $c[?25h $c[HJ $c[8;6H $c[?25l $c[HJ $c[9;1H $c[?25h")
  println(a.getString())
}