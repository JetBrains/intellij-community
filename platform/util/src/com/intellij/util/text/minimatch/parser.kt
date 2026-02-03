/**
 * The MIT License (MIT)

 * Copyright (c) 2015 Angelo

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.intellij.util.text.minimatch

import com.intellij.openapi.util.text.StringUtil
import java.util.regex.Pattern

internal abstract class ParseItem(val source: String?) {
  abstract fun match(file: CharSequence, options: MinimatchOptions): Boolean

  companion object {
    @JvmField
    val Empty: ParseItem = LiteralItem("")
  }
}

internal class ParseResult(val item: ParseItem, val isB: Boolean)

internal class GlobStar : ParseItem(null) {
  override fun match(file: CharSequence, options: MinimatchOptions): Nothing = throw UnsupportedOperationException()

  override fun toString(): String = "GlobStar"
}

internal class LiteralItem(source: String) : ParseItem(source) {
  override fun match(file: CharSequence, options: MinimatchOptions): Boolean = if (options.nocase) StringUtil.equalsIgnoreCase(file, source) else StringUtil.equals(file, source)

  // TODO Auto-generated method stub
  override fun toString(): String = "Literal(\"$source\")"
}

internal class MagicItem(source: String, options: MinimatchOptions) : ParseItem(source) {
  private val pattern = Pattern.compile("^$source$", if (options.nocase) Pattern.CASE_INSENSITIVE else 0)

  override fun match(file: CharSequence, options: MinimatchOptions): Boolean = pattern.matcher(file).matches()

  override fun toString(): String = "RegExp(\"$source\")"
}

internal class ParseContext {
  // State char
  @JvmField
  var stateChar: Char? = null
  @JvmField
  var re: String = ""
  @JvmField
  var hasMagic: Boolean = false
}

internal data class PatternListItem(val type: Char, val start: Int, val reStart: Int, var reEnd: Int = 0)