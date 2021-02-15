// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrStubFileElementType;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * @author ilyas
 */
public class GroovyParserDefinition implements ParserDefinition {
  public static final IStubFileElementType GROOVY_FILE = new GrStubFileElementType(GroovyLanguage.INSTANCE);

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new GroovyLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new GroovyParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return GROOVY_FILE;
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return TokenSets.COMMENTS_TOKEN_SET;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSets.STRING_LITERALS;
  }

  @Override
  @NotNull
  public PsiElement createElement(ASTNode node) {
    return GroovyPsiCreator.createElement(node);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new GroovyFileImpl(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final IElementType lType = left.getElementType();
    final IElementType rType = right.getElementType();

    if (rType == GroovyTokenTypes.kIMPORT && lType != TokenType.WHITE_SPACE) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    else if (lType == MODIFIER_LIST && rType == MODIFIER_LIST) {
      return SpaceRequirements.MUST;
    }
    if (lType == GroovyTokenTypes.mSEMI) {
      return SpaceRequirements.MAY;
    }
    if (lType == GroovyTokenTypes.mSL_COMMENT) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    if (lType == GroovyTokenTypes.mNLS || lType == GroovyDocTokenTypes.mGDOC_COMMENT_START) {
      return SpaceRequirements.MAY;
    }
    if (lType == GroovyTokenTypes.mGT) return SpaceRequirements.MUST;
    if (rType == GroovyTokenTypes.mLT) return SpaceRequirements.MUST;

    final ASTNode parent = TreeUtil.findCommonParent(left, right);
    if (parent == null || ArrayUtil.contains(parent.getElementType(), GSTRING, REGEX, GSTRING_INJECTION,
                                             GroovyTokenTypes.mREGEX_LITERAL, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL)) {
      return SpaceRequirements.MUST_NOT;
    }

    return LanguageUtil.canStickTokensTogetherByLexer(left, right, new GroovyLexer());
  }
}
