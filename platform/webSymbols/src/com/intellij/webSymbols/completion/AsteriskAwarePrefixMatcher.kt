// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder

class AsteriskAwarePrefixMatcher private constructor(prefix: String, delegate: PrefixMatcher) : PrefixMatcher(prefix) {

  private val myDelegate: PrefixMatcher

  constructor(delegate: PrefixMatcher) : this(delegate.prefix, delegate) {}

  init {
    myDelegate = delegate.cloneWithPrefix(convert(prefix, true))
  }

  override fun prefixMatches(name: String): Boolean = myDelegate.prefixMatches(convert(name))
  override fun prefixMatches(element: LookupElement): Boolean = myDelegate.prefixMatches(convert(element))
  override fun isStartMatch(name: String): Boolean = myDelegate.isStartMatch(convert(name))
  override fun isStartMatch(element: LookupElement): Boolean = myDelegate.isStartMatch(convert(element))
  override fun matchingDegree(string: String): Int = myDelegate.matchingDegree(convert(string))

  override fun cloneWithPrefix(prefix: String): PrefixMatcher =
    if (prefix == myPrefix) this
    else AsteriskAwarePrefixMatcher(prefix, myDelegate)

  companion object {

    private const val ASTERISK = '*'
    private const val ASTERISK_REPLACEMENT = '⭐'

    private fun convert(element: LookupElement): LookupElement =
      LookupElementBuilder.create(convert(element.lookupString))
        .withCaseSensitivity(element.isCaseSensitive)
        .withLookupStrings(element.allLookupStrings.mapTo(mutableSetOf()) { convert(it) })

    private fun convert(input: String, forPattern: Boolean = false): String =
      if (input.isNotEmpty() && input[0] == ASTERISK) {
        if (forPattern) {
          ASTERISK_REPLACEMENT + CamelHumpMatcher.applyMiddleMatching(input.substring(1))
        }
        else {
          val chars = input.toCharArray()
          chars[0] = ASTERISK_REPLACEMENT
          String(chars)
        }
      }
      else input
  }
}
