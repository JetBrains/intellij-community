// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text

import com.intellij.util.text.CharArrayCharSequence
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

class LineTokenizer(private val myText: CharSequence) {
  var offset: Int = 0
    private set
  var length: Int = 0
    private set
  var lineSeparatorLength: Int = 0
    private set
  private var atEnd = false

  init {
    advance()
  }

  constructor(text: CharArray, startOffset: Int, endOffset: Int) : this(CharArrayCharSequence(text, startOffset, endOffset))

  fun atEnd(): Boolean {
    return atEnd
  }

  fun advance() {
    var i = this.offset + this.length + this.lineSeparatorLength
    val textLength = myText.length
    if (i >= textLength) {
      atEnd = true
      return
    }
    while (i < textLength) {
      val c = myText[i]
      if (c == '\r' || c == '\n') break
      i++
    }

    this.offset += this.length + this.lineSeparatorLength
    this.length = i - this.offset

    this.lineSeparatorLength = 0
    if (i == textLength) return

    val first = myText[i]
    if (first == '\r' || first == '\n') {
      this.lineSeparatorLength = 1
    }

    i++
    if (i == textLength) return

    val second = myText[i]
    if (first == '\r' && second == '\n') {
      this.lineSeparatorLength = 2
    }
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun tokenize(chars: CharSequence?, includeSeparators: Boolean, skipLastEmptyLine: Boolean = true): Array<String> {
      val strings = tokenizeIntoList(chars, includeSeparators, skipLastEmptyLine)
      return if (strings.isEmpty()) EMPTY_STRING_ARRAY else strings.toTypedArray()
    }

    private val EMPTY_STRING_ARRAY: Array<String> = emptyArray<String>()

    @JvmStatic
    @JvmOverloads
    fun tokenizeIntoList(chars: CharSequence?, includeSeparators: Boolean, skipLastEmptyLine: Boolean = true): List<String> {
      if (chars == null || chars.isEmpty()) {
        return mutableListOf()
      }

      val tokenizer = LineTokenizer(chars)
      val lines: MutableList<String> = ArrayList()
      while (!tokenizer.atEnd()) {
        val offset = tokenizer.offset
        val line = if (includeSeparators) {
          chars.subSequence(offset, offset + tokenizer.length + tokenizer.lineSeparatorLength).toString()
        }
        else {
          chars.subSequence(offset, offset + tokenizer.length).toString()
        }
        lines.add(line)
        tokenizer.advance()
      }

      if (!skipLastEmptyLine && stringEndsWithSeparator(tokenizer)) lines.add("")

      return lines
    }

    @JvmStatic
    fun calcLineCount(chars: CharSequence, skipLastEmptyLine: Boolean): Int {
      var lineCount = 0
      if (chars.isNotEmpty()) {
        val tokenizer = LineTokenizer(chars)
        while (!tokenizer.atEnd()) {
          lineCount += 1
          tokenizer.advance()
        }
        if (!skipLastEmptyLine && stringEndsWithSeparator(tokenizer)) {
          lineCount += 1
        }
      }
      return lineCount
    }

    @JvmStatic
    @JvmOverloads
    fun tokenize(chars: CharArray, includeSeparators: Boolean, skipLastEmptyLine: Boolean = true): Array<String> {
      return tokenize(chars, 0, chars.size, includeSeparators, skipLastEmptyLine)
    }

    @JvmOverloads
    fun tokenize(
      chars: CharArray,
      startOffset: Int,
      endOffset: Int,
      includeSeparators: Boolean,
      skipLastEmptyLine: Boolean = true,
    ): Array<String> {
      return tokenize(CharArrayCharSequence(chars, startOffset, endOffset), includeSeparators, skipLastEmptyLine)
    }

    private fun stringEndsWithSeparator(tokenizer: LineTokenizer): Boolean {
      return tokenizer.lineSeparatorLength > 0
    }
  }
}
