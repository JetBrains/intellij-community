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
