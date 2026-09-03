/**
 * MIT License
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */
package com.intellij.util.text.simpleGlob

import com.intellij.openapi.util.text.StringUtil

private fun String.dropFirst() = this.drop(1)


/**
 * Simple glob implementation for converting VSC's simple glob to regex.
 * Original implemented in "vscode-js-debug". Logic preserved for user experience as
 * users are likely already familiar with this pattern syntax.
 */
fun simpleGlobsToRe(globs: List<String>): List<String> {
  val res = mutableListOf<String>()

  for (i in globs.indices) {
    val glob = globs[i]

    if (glob.startsWith("!")) {
      val nonNegated = globToRe(glob.dropFirst())

      for (j in res.indices) {
        res[j] = "^(?!${nonNegated.dropFirst()})${res[j].dropFirst()}"
      }
    }
    else {
      res.add(globToRe(glob))
    }
  }

  return res
}

private fun globToRe(glob: String): String {
  val parts = glob.split('/')
  val regexParts = mutableListOf<String>()

  for (j in parts.indices) {
    val part = parts[j]
    if (part == "**") {
      when (j) {
        0 -> regexParts.add("(.+/)?")
        parts.size - 1 -> { /* nop */ }
        else -> regexParts.add(".*/")
      }
    }
    else if (j == parts.size - 1 && part.endsWith("**")) {
      // this part is also modified to allow for trailing ** to mean ".*"
      regexParts.add(segmentToRe(part.dropLast(2)))
      regexParts.add(".*")
    }
    else {
      regexParts.add(segmentToRe(part))
      regexParts.add(if (j < parts.size - 1) "\\/" else "$")
    }
  }

  return "^${regexParts.joinToString("")}"
}

// this part is modified to support "?"
private fun segmentToRe(part: String): String {
  return if (part.any { it == '*' || it == '?' }) {
    part.split('*').joinToString("[^/]*") { starChunk ->
      starChunk.split('?').joinToString("[^/]") {
        StringUtil.escapeToRegexp(it)
      }
    }
  }
  else {
    StringUtil.escapeToRegexp(part)
  }
}
