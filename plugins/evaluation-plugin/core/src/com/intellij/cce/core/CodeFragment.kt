// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

class CodeFragment(val offset: Int, val length: Int) {
  private val children = mutableListOf<CodeElement>()
  lateinit var path: String
  lateinit var text: String

  fun getChildren(): List<CodeElement> = children.sortedBy { it.offset }

  fun addChild(token: CodeElement) {
    if (children.any { it.offset == token.offset }) return
    children.add(token)
  }
}
