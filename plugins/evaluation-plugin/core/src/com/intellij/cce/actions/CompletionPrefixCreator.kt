package com.intellij.cce.actions

abstract class CompletionPrefixCreator(val completePrevious: Boolean) {
  abstract fun getPrefix(text: String): String
}

class NoPrefixCreator : CompletionPrefixCreator(false) {
  override fun getPrefix(text: String): String {
    return ""
  }
}

class SimplePrefixCreator(completePrevious: Boolean, private val n: Int) : CompletionPrefixCreator(completePrevious) {
  override fun getPrefix(text: String): String {
    return if (text.length < n) return text else text.substring(0, n)
  }
}

class CapitalizePrefixCreator(completePrevious: Boolean) : CompletionPrefixCreator(completePrevious) {
  override fun getPrefix(text: String): String {
    return text.filterIndexed { i, ch -> i == 0 || ch.isUpperCase() }
  }
}