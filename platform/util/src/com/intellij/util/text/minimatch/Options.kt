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

data class MinimatchOptions(
  var allowWindowsPaths: Boolean = false,
  var nocomment: Boolean = false,
  var nonegate: Boolean = false,
  var nobrace: Boolean = false,
  var noglobstar: Boolean = false,
  var nocase: Boolean = false,
  var dot: Boolean = false,
  var noext: Boolean = false,
  var matchBase: Boolean = false,
  var flipNegate: Boolean = false) {

  override fun toString(): String {
    val sb = StringBuilder()
    appendIfTrue(sb, "allowWindowsPaths", allowWindowsPaths)
    appendIfTrue(sb, "nocomment", nocomment)
    appendIfTrue(sb, "nonegate", nonegate)
    appendIfTrue(sb, "nobrace", nobrace)
    appendIfTrue(sb, "noglobstar", noglobstar)
    appendIfTrue(sb, "nocase", nocase)
    appendIfTrue(sb, "dot", dot)
    appendIfTrue(sb, "noext", noext)
    appendIfTrue(sb, "matchBase", matchBase)
    appendIfTrue(sb, "flipNegate", flipNegate)
    if (sb.isEmpty()) {
      return "[]"
    }
    else {
      sb.insert(0, "[")
      sb.setLength(sb.length - 2)
      sb.append("]")
      return sb.toString()
    }
  }
}

private fun appendIfTrue(str: StringBuilder, name: String, value: Boolean) {
  if (value) {
    str.append(name)
    str.append("=true, ")
  }
}
