// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lang.parser.GeneratedParserUtilBase.DUMMY_BLOCK
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.devkit.apiDump.lang.elementTypes.ADFileNodeType
import com.intellij.devkit.apiDump.lang.lexer.ADLexer
import com.intellij.devkit.apiDump.lang.parser.ADParser
import com.intellij.devkit.apiDump.lang.psi.ADElementTypes
import com.intellij.devkit.apiDump.lang.psi.impl.ADFileImpl

internal class ADParserDefinition : ParserDefinition, ASTFactory() {
  override fun createLexer(project: Project?): Lexer = ADLexer()

  override fun createParser(project: Project?): PsiParser = ADParser()

  override fun getFileNodeType(): IFileElementType = ADFileNodeType

  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode): PsiElement =
    throw IllegalArgumentException("Not a ApiDump node: $node (${node.elementType}, ${node.elementType.language})")

  override fun createFile(viewProvider: FileViewProvider): PsiFile =
    ADFileImpl(viewProvider)

  override fun createComposite(type: IElementType): CompositeElement? {
    if (type == DUMMY_BLOCK) {
      // todo remove this check after adding recovery
      return CompositeElement(type)
    }
    return ADElementTypes.Factory.createElement(type)
  }
}