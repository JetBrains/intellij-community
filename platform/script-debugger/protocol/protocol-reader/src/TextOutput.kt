package org.jetbrains.protocolReader

import java.util.*

val EMPTY_CHARS: CharArray = CharArray(0)
private val indentGranularity = 2

class TextOutput(val out: StringBuilder) {
  private var identLevel: Int = 0
  private var indents = arrayOf(EMPTY_CHARS)
  private var justNewLined: Boolean = false

  fun indentIn(): TextOutput {
    ++identLevel
    if (identLevel >= indents.size) {
      // Cache a new level of indentation string.
      val newIndentLevel = CharArray(identLevel * indentGranularity)
      Arrays.fill(newIndentLevel, ' ')
      val newIndents = arrayOfNulls<CharArray>(indents.size + 1)
      System.arraycopy(indents, 0, newIndents, 0, indents.size)
      newIndents[identLevel] = newIndentLevel
      indents = newIndents as Array<CharArray>
    }
    return this
  }

  fun indentOut(): TextOutput {
    --identLevel
    return this
  }

  fun newLine(): TextOutput {
    out.append('\n')
    justNewLined = true
    return this
  }

  fun append(value: Double): TextOutput {
    maybeIndent()
    out.append(value)
    return this
  }

  fun append(value: Boolean): TextOutput {
    maybeIndent()
    out.append(value)
    return this
  }

  fun append(value: Int): TextOutput {
    maybeIndent()
    out.append(value)
    return this
  }

  fun append(c: Char): TextOutput {
    maybeIndent()
    out.append(c)
    return this
  }

  fun append(s: CharArray) {
    maybeIndent()
    out.append(s)
  }

  fun append(s: CharSequence): TextOutput {
    maybeIndent()
    out.append(s)
    return this
  }

  fun append(s: CharSequence, start: Int): TextOutput {
    maybeIndent()
    out.append(s, start, s.length)
    return this
  }

  fun openBlock(): TextOutput {
    openBlock(true)
    return this
  }

  inline fun block(addNewLine: Boolean = true, writer: () -> Unit): TextOutput {
    openBlock(addNewLine)
    writer()
    closeBlock()
    return this
  }

  fun openBlock(addNewLine: Boolean) {
    space().append('{')
    if (addNewLine) {
      newLine()
    }
    indentIn()
  }

  fun closeBlock() {
    indentOut().newLine().append('}')
  }

  fun comma() = append(',').space()

  fun space() = append(' ')

  fun doc(description: String?): TextOutput {
    if (description == null) {
      return this
    }
    return append("/**").newLine().append(" * ").append(description).newLine().append(" */").newLine()
  }

  fun quote(s: CharSequence) = append('"').append(s).append('"')

  fun maybeIndent() {
    if (justNewLined) {
      out.append(indents[identLevel])
      justNewLined = false
    }
  }

  fun appendEscapedName(name: String): TextOutput {
    val isEscapeName = name == "object" || name == "fun"
    if (isEscapeName) {
      out.append('`')
    }
    out.append(name)
    if (isEscapeName) {
      out.append('`')
    }
    return this
  }

  operator fun plus(value: String) = append(value)
}
