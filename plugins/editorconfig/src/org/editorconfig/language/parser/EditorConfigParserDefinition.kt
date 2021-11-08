// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.editorconfig.configmanagement.lexer.EditorConfigLexerFactory
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigPsiFile

class EditorConfigParserDefinition : ParserDefinition {
  override fun createLexer(project: Project) = EditorConfigLexerFactory.getAdapter()
  override fun createParser(project: Project): PsiParser = EditorConfigParser()

  override fun getCommentTokens() = COMMENTS
  override fun getWhitespaceTokens() = WHITE_SPACES
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun getFileNodeType() = FILE

  override fun createFile(viewProvider: FileViewProvider): PsiFile = EditorConfigPsiFile(viewProvider)
  override fun createElement(node: ASTNode): PsiElement = EditorConfigElementTypes.Factory.createElement(node)

  private companion object {
    val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
    val COMMENTS = TokenSet.create(EditorConfigElementTypes.LINE_COMMENT)
    val FILE = IFileElementType(EditorConfigLanguage)
  }
}
