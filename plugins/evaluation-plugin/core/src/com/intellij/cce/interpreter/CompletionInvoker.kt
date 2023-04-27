package com.intellij.cce.interpreter

import com.intellij.cce.actions.TextRange
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties

interface CompletionInvoker {
  fun moveCaret(offset: Int)
  fun callCompletion(expectedText: String, prefix: String?): Lookup
  fun finishCompletion(expectedText: String, prefix: String): Boolean
  fun printText(text: String)
  fun deleteRange(begin: Int, end: Int)
  fun openFile(file: String): String
  fun closeFile(file: String)
  fun isOpen(file: String): Boolean
  fun save()
  fun getText(): String
  fun emulateUserSession(expectedText: String,
                         nodeProperties: TokenProperties,
                         offset: Int): Session

  fun emulateCompletionGolfSession(expectedLine: String, completableRanges: List<TextRange>, offset: Int): Session
}
