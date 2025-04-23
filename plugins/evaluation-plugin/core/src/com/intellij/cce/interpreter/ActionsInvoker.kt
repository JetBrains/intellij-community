// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.interpreter

interface ActionsInvoker {
  fun moveCaret(offset: Int)
  fun rename(text: String)
  fun printText(text: String)
  fun deleteRange(begin: Int, end: Int)
  fun selectRange(begin: Int, end: Int)
  fun delay(seconds: Int)
  fun openFile(file: String): String
  fun closeFile(file: String)
  fun optimiseImports(file: String)
  fun isOpen(file: String): Boolean
  fun save()
  fun getText(): String
}
