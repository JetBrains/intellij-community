/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  private static final IElementType[] STRINGS = new IElementType[]{
    GSTRING, REGEX, GSTRING_INJECTION, GroovyTokenTypes.mREGEX_LITERAL, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL
  };

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new GroovyLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new GroovyParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return GROOVY_FILE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSets.WHITE_SPACE_TOKEN_SET;
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
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new GroovyFileImpl(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final IElementType lType = left.getElementType();
    final IElementType rType = right.getElementType();

    if (rType == GroovyTokenTypes.kIMPORT && lType != TokenType.WHITE_SPACE) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    else if (lType == MODIFIERS && rType == MODIFIERS) {
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
    if (parent == null || ArrayUtil.contains(parent.getElementType(), STRINGS)) {
      return SpaceRequirements.MUST_NOT;
    }

    return LanguageUtil.canStickTokensTogetherByLexer(left, right, new GroovyLexer());
  }
}
