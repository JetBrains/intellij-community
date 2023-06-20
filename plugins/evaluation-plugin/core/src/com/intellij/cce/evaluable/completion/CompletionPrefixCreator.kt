// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

interface CompletionPrefixCreator {
  fun getPrefix(text: String): String
}

class NoPrefixCreator : CompletionPrefixCreator {
  override fun getPrefix(text: String): String {
    return ""
  }
}

class SimplePrefixCreator(private val n: Int) : CompletionPrefixCreator {
  override fun getPrefix(text: String): String {
    return if (text.length < n) return text else text.substring(0, n)
  }
}

class CapitalizePrefixCreator : CompletionPrefixCreator {
  override fun getPrefix(text: String): String {
    return text.filterIndexed { i, ch -> i == 0 || ch.isUpperCase() }
  }
}
