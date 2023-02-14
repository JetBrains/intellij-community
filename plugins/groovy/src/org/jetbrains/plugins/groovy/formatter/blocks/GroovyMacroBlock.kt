// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter.blocks

import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.groovy.formatter.FormattingContext

class GroovyMacroBlock(node: ASTNode, context: FormattingContext) : GroovyBlock(node, Indent.getNoneIndent(), Wrap.createWrap(WrapType.NONE, false), context) {
  override fun getSubBlocks(): MutableList<Block> {
    if (mySubBlocks == null) {
      mySubBlocks = mutableListOf()
    }
    return mySubBlocks
  }

  override fun isLeaf(): Boolean = true
}