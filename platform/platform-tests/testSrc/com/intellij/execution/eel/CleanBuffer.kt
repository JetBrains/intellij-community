// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

// Removes ESC chars
internal class CleanBuffer {
  private companion object {
    val NEW_LINES = Regex("\r?\n")
    val OS_COMMAND_SET_TITLE = Regex("\\x1b]0;[^\\x1b]+(:?\\x9C|\\x07|\\x1b\\x5c)")
    val CSI_CURSOR = Regex("\\x1b\\[\\?[0-9]+[a-zA-Z]")
    fun removeEsc(string: String): String {
      var string = OS_COMMAND_SET_TITLE.replace(string, "")
      string = CSI_CURSOR.replace(string, "")
      return NEW_LINES.replace(string, "")
    }
  }

  private val buffer = StringBuilder()
  fun add(line: String) {
    val newString = removeEsc(buffer.append(line).toString())
    buffer.clear()
    buffer.append(newString)
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