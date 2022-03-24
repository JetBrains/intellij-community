package com.intellij.cce.core

class CodeFragment(val offset: Int, val length: Int) {
  private val children = mutableListOf<CodeToken>()
  lateinit var path: String
  lateinit var text: String

  fun getChildren(): List<CodeToken> = children.sortedBy { it.offset }

  fun addChild(token: CodeToken) {
    if (children.any { it.offset == token.offset }) return
    children.add(token)
  }
}