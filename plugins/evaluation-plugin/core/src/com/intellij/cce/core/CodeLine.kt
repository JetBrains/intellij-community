// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.core

class CodeLine(override val text: String,
               override val offset: Int) : CodeElement {
  private val children: MutableList<CodeToken> = mutableListOf()

  fun getChildren(): List<CodeToken> = children.sortedBy { it.offset }

  fun addChild(token: CodeToken) {
    children.add(token)
  }
}
