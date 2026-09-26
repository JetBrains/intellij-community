// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterLazyParseableNode
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.ILightLazyParseableElementType
import com.intellij.util.CharTable
import com.intellij.util.diff.FlyweightCapableTreeStructure

/**
 * A node in light tree
 * Represents a chameleon consisting of one or several lexemes and supporting light parsing
 */
internal class LazyParseableToken(
  val parentStructure: MyTreeStructure,
  val startIndex: Int,
  val endIndex: Int,
) : TokenRange(), LighterLazyParseableNode {
  private var myParsed: FlyweightCapableTreeStructure<LighterASTNode>? = null

  override fun getContainingFile(): PsiFile? {
    return this.nodeData.file
  }

  override fun getCharTable(): CharTable? {
    return this.nodeData.charTable
  }

  fun parseContents(): FlyweightCapableTreeStructure<LighterASTNode> {
    var parsed = myParsed
    if (parsed == null) {
      parsed = (tokenType as ILightLazyParseableElementType).parseContents(this)
      myParsed = parsed
    }
    return parsed
  }

  override fun accept(visitor: LighterLazyParseableNode.Visitor): Boolean {
    for (i in startIndex..<endIndex) {
      val type = this.nodeData.getLexemeType(i)
      if (!visitor.visit(type)) {
        return false
      }
    }

    return true
  }

  val parsedTokenSequence: TokenList?
    get() {
      val tokenCount = endIndex - startIndex
      if (tokenCount == 1) return null // not expand single lazy parseable token case

      val lexStarts = IntArray(tokenCount + 1)
      System.arraycopy(nodeData.lexStarts, startIndex, lexStarts, 0, tokenCount + 1)
      val diff = nodeData.lexStarts[startIndex]
      (0 until tokenCount).forEach { lexStarts[it] -= diff }
      lexStarts[tokenCount] = endOffset - startOffset

      val lexTypes = arrayOfNulls<SyntaxElementType>(tokenCount + 1)
      System.arraycopy(nodeData.lexTypes, startIndex, lexTypes, 0, tokenCount)

      @Suppress("UNCHECKED_CAST")
      return TokenList(lexStarts, lexTypes as Array<SyntaxElementType>, tokenCount, text)
    }
}