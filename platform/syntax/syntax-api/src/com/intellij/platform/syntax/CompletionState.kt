// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.SyntaxGeneratedParserUtilBase.Companion.isWhitespaceOrComment

class CompletionState(val offset: Int) : Function<Any?, String?> {
  val items: MutableCollection<String?> = HashSet<String?>()

  fun convertItem(o: Any): String {
    return if (o is Array<*> && o.isArrayOf<Any>())
      o.joinToString(" ") { it -> it.toString() }
    else o.toString()
  }

  override fun `fun`(o: Any): String? {
    return convertItem(o)
  }

  fun addItem(builder: SyntaxTreeBuilder, text: String) {
    items.add(text)
  }

  fun prefixMatches(builder: SyntaxTreeBuilder, text: String): Boolean {
    val builderOffset: Int = builder.currentOffset
    var diff = offset - builderOffset
    val length = text.length
    if (diff == 0) {
      return true
    }
    else if (diff > 0 && diff <= length) {
      val fragment = builder.text.subSequence(builderOffset, offset)
      return prefixMatches(fragment.toString(), text)
    }
    else if (diff < 0) {
      var i = -1
      while (true) {
        val type: SyntaxElementType? = builder.rawLookup(i)
        val tokenStart: Int = builder.rawTokenTypeStart(i)
        if (isWhitespaceOrComment(builder, type)) {
          diff = offset - tokenStart
        }
        else if (type != null && tokenStart < offset) {
          val fragment = builder.text.subSequence(tokenStart, offset)
          if (prefixMatches(fragment.toString(), text)) {
            diff = offset - tokenStart
          }
          break
        }
        else break
        i--
      }
      return diff >= 0 && diff < length
    }
    return false
  }

  fun prefixMatches(prefix: String, variant: String): Boolean {
    val matches: Boolean = CamelHumpMatcher(prefix, false).prefixMatches(variant.replace(' ', '_'))
    if (matches && prefix[prefix.length - 1].isWhitespace()) {
      return variant.startsWith(prefix, true)
    }
    return matches
  }
}