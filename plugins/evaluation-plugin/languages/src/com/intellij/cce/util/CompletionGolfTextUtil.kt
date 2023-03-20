package com.intellij.cce.util

object CompletionGolfTextUtil {
  fun String.isValuableString(): Boolean {
    return find { it.isLetterOrDigit() || it == '\'' || it == '"' } != null
  }
}
