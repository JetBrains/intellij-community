// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editorconfig.common.plugin

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.editorconfig.common.syntax.parser.EditorConfigParser
import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class EditorConfigParserDefinition : ParserDefinition {
  override fun createLexer(project: Project): Lexer = EditorConfigLexerFactory.getAdapter()
  override fun createParser(project: Project): PsiParser = EditorConfigParser()

  override fun getCommentTokens(): TokenSet = EditorConfigTokenSets.COMMENTS
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun getFileNodeType(): IFileElementType = FILE

  override fun createFile(viewProvider: FileViewProvider): PsiFile = EditorConfigPsiFile(viewProvider)
  override fun createElement(node: ASTNode): PsiElement = EditorConfigElementTypes.Factory.createElement(node)

  private val FILE = IFileElementType(EditorConfigLanguage)
}

private object EditorConfigTokenSets {
  val COMMENTS: TokenSet = TokenSet.create(EditorConfigElementTypes.LINE_COMMENT)
}
