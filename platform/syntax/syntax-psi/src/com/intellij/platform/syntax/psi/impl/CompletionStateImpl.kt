// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.openapi.util.Key
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.runtime.CompletionState
import com.intellij.platform.syntax.runtime.isWhitespaceOrComment

val SYNTAX_COMPLETION_STATE_KEY: Key<CompletionState?> = Key.create<CompletionState?>("SYNTAX_COMPLETION_STATE_KEY")

class CompletionStateImpl(val offset: Int) : CompletionState {

  override val items: MutableCollection<String?> = HashSet<String?>()

  override fun convertItem(o: Any): String {
    return if (o is Array<*> && o.isArrayOf<Any>())
      o.joinToString(" ") { it -> it.toString() }
    else o.toString()
  }

  override fun invoke(o: Any): String? {
    return convertItem(o)
  }

  override fun addItem(builder: SyntaxTreeBuilder, text: String) {
    items.add(text)
  }

  override fun prefixMatches(builder: SyntaxTreeBuilder, text: String): Boolean {
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

  override fun prefixMatches(prefix: String, variant: String): Boolean {
    val matches: Boolean = CamelHumpMatcher(prefix, false).prefixMatches(variant.replace(' ', '_'))
    if (matches && prefix[prefix.length - 1].isWhitespace()) {
      return variant.startsWith(prefix, true)
    }
    return matches
  }
}