// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class GroovyIndexPatternBuilder : IndexPatternBuilder {

  override fun getIndexingLexer(file: PsiFile): Lexer? {
    return if (file is GroovyFile) GroovyLexer() else null
  }

  override fun getCommentTokenSet(file: PsiFile): TokenSet? {
    return TokenSets.ALL_COMMENT_TOKENS
  }

  override fun getCommentStartDelta(tokenType: IElementType): Int {
    return 0
  }

  override fun getCommentEndDelta(tokenType: IElementType): Int {
    return if (tokenType === GroovyTokenTypes.mML_COMMENT) 2 else 0
  }
}
