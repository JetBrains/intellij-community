package com.intellij.cce.interpreter

import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties

interface ActionsInvoker {
  fun moveCaret(offset: Int)
  fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session
  fun rename(text: String)
  fun printText(text: String)
  fun deleteRange(begin: Int, end: Int)
  fun openFile(file: String): String
  fun closeFile(file: String)
  fun isOpen(file: String): Boolean
  fun save()
  fun getText(): String
}
