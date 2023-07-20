package com.intellij.cce.core

class CodeLine(override val text: String,
               override val offset: Int) : CodeElement {
  private val children: MutableList<CodeToken> = mutableListOf()

  fun getChildren(): List<CodeToken> = children.sortedBy { it.offset }

  fun addChild(token: CodeToken) {
    children.add(token)
  }
}
