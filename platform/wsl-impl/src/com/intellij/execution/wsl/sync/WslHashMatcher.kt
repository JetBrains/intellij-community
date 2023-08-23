// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.util.containers.map2Array

/**
 * Must be kept in sync with wslhash.c.
 */
abstract class WslHashMatcher(val code: String, val pattern: String) {
  /**
   * @return true if [fileName] matches, false otherwise.
   */
  abstract fun matches(fileName: String): Boolean

  companion object Factory {

    fun extension(ext: String): WslHashMatcher {
      return RegexMatcher(Regex("\\.${ext.escapeRegexChars()}$"))
    }

    fun extensions(vararg exts: String): Array<WslHashMatcher> {
      return exts.map2Array { extension(it) }
    }

    fun basename(name: String): WslHashMatcher {
      return RegexMatcher(Regex("^${name.escapeRegexChars()}(\\..+)?$"))
    }

    fun basenames(vararg names: String): Array<WslHashMatcher> {
      return names.map2Array { basename(it) }
    }

    fun fullname(name: String): WslHashMatcher {
      return RegexMatcher(Regex("^${name.escapeRegexChars()}$"))
    }

    fun fullnames(vararg names: String): Array<WslHashMatcher> {
      return names.map2Array { fullname(it) }
    }

    private fun String.escapeRegexChars(): String {
      return this.fold(StringBuilder()) { acc, chr ->
        acc.append(if ("<([{\\^-=$!|]})?*+.>".contains(chr)) "\\$chr" else "$chr")
      }.toString()
    }
  }

  /**
   * @param regex must be a [POSIX ERE](https://en.wikipedia.org/wiki/Regular_expression#POSIX_extended).
   * @see wslhash.c
   */
  private class RegexMatcher(private val regex: Regex) : WslHashMatcher("rgx", regex.pattern) {
    override fun matches(fileName: String): Boolean {
      return regex.containsMatchIn(fileName)
    }
  }
}