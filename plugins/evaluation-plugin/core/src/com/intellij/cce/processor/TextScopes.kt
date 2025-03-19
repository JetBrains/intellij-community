// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.processor

import java.util.*

class TextScopes(text: String) {

  private val closed: Stack<Char> = Stack()
  private val opened: Stack<Char> = Stack()

  init {
    var multiLineComment = false
    var stringLiteral = false
    var charLiteral = false
    for (line in text.lines()) {
      if (!multiLineComment) {
        val cleanLine = when {
          "/*" in line && "*/" in line -> line.removeRange(line.indexOf("/*"), line.lastIndexOf("*/"))
          "/*" in line -> {
            multiLineComment = true
            line.substringBefore("/*")
          }
          "*/" in line -> {
            multiLineComment = false
            line.substringAfter("*/")
          }
          else -> line
        }.substringBefore("//").removeEscapedChars()

        for (char in cleanLine) {
          if (char == '"' && !charLiteral) {
            stringLiteral = !stringLiteral
          }
          else if (char == '\'' && !stringLiteral) {
            charLiteral = !charLiteral
          }
          if (stringLiteral || charLiteral) continue
          if (SCOPE_CHARS.containsKey(char)) {
            opened.add(char)
          }
          else if (SCOPE_CHARS.containsValue(char)) {
            if (opened.isEmpty()) {
              closed.add(char)
            }
            else {
              opened.pop()
            }
          }
        }
      }
    }
  }

  val closedCount: Int = closed.size
  val reversedOpened: String = opened.map { SCOPE_CHARS[it] }.joinToString(separator = "")

  private fun String.removeEscapedChars(): String {
    return this.replace("\\{", "")
      .replace("\\}", "")
      .replace("\\(", "")
      .replace("\\)", "")
      .replace("\\[", "")
      .replace("\\]", "")
      .replace("\\\"", "")
      .replace("\\\'", "")
  }

  private companion object {
    private val SCOPE_CHARS = mapOf('{' to '}', '[' to ']', '(' to ')')
  }
}