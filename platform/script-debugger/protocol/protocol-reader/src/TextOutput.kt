package org.jetbrains.protocolReader

import java.util.Arrays

public val EMPTY_CHARS: CharArray = CharArray(0)
private val indentGranularity = 2

public class TextOutput(public val out: StringBuilder) {
  private var identLevel: Int = 0
  private var indents = arrayOf(EMPTY_CHARS)
  private var justNewLined: Boolean = false

  public fun indentIn(): TextOutput {
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

  public fun indentOut(): TextOutput {
    --identLevel
    return this
  }

  public fun newLine(): TextOutput {
    out.append('\n')
    justNewLined = true
    return this
  }

  public fun append(value: Double): TextOutput {
    maybeIndent()
    out.append(value)
    return this
  }

  public fun append(value: Boolean): TextOutput {
    maybeIndent()
    out.append(value)
    return this
  }

  public fun append(value: Int): TextOutput {
    maybeIndent()
    out.append(value)
    return this
  }

  public fun append(c: Char): TextOutput {
    maybeIndent()
    out.append(c)
    return this
  }

  public fun append(s: CharArray) {
    maybeIndent()
    out.append(s)
  }

  public fun append(s: CharSequence): TextOutput {
    maybeIndent()
    out.append(s)
    return this
  }

  public fun append(s: CharSequence, start: Int): TextOutput {
    maybeIndent()
    out.append(s, start, s.length())
    return this
  }

  public fun openBlock(): TextOutput {
    openBlock(true)
    return this
  }

  public fun openBlock(addNewLine: Boolean) {
    space().append('{')
    if (addNewLine) {
      newLine()
    }
    indentIn()
  }

  public fun closeBlock() {
    indentOut().newLine().append('}')
  }

  public fun comma(): TextOutput {
    return append(',').space()
  }

  public fun space(): TextOutput {
    return append(' ')
  }

  public fun semi(): TextOutput {
    return append(';')
  }

  public fun doc(description: String?): TextOutput {
    if (description == null) {
      return this
    }
    return append("/**").newLine().append(" * ").append(description).newLine().append(" */").newLine()
  }

  public fun quote(s: CharSequence): TextOutput {
    return append('"').append(s).append('"')
  }

  public fun maybeIndent() {
    if (justNewLined) {
      out.append(indents[identLevel])
      justNewLined = false
    }
  }
}
