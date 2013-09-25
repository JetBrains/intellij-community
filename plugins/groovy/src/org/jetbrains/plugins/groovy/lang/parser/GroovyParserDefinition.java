/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrStubFileElementType;

import static com.intellij.lang.ParserDefinition.SpaceRequirements.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * @author ilyas
 */
public class GroovyParserDefinition implements ParserDefinition {
  public static final IStubFileElementType GROOVY_FILE = new GrStubFileElementType(GroovyFileType.GROOVY_LANGUAGE);

  @NotNull
  public Lexer createLexer(Project project) {
    return new GroovyLexer();
  }

  public PsiParser createParser(Project project) {
    return new GroovyParser();
  }

  public IFileElementType getFileNodeType() {
    return GROOVY_FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSets.WHITE_SPACE_TOKEN_SET;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return TokenSets.COMMENTS_TOKEN_SET;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSets.STRING_LITERALS;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    return GroovyPsiCreator.createElement(node);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new GroovyFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final IElementType lType = left.getElementType();
    final IElementType rType = right.getElementType();

    if (rType == kIMPORT && lType != TokenType.WHITE_SPACE) {
      return MUST_LINE_BREAK;
    }
    else if (lType == MODIFIERS && rType == MODIFIERS) {
      return MUST;
    }
    if (lType == mSEMI || lType == mSL_COMMENT) {
      return MUST_LINE_BREAK;
    }    
    if (lType == mNLS || lType == mGDOC_COMMENT_START) {
      return MAY;
    }
    if (lType == mGT) return MUST;
    if (rType == mLT) return MUST;

    final IElementType parentType = left.getTreeParent().getElementType();
    if (parentType == GSTRING ||
        parentType == REGEX ||
        parentType == GSTRING_INJECTION ||
        parentType == mREGEX_LITERAL ||
        parentType == mDOLLAR_SLASH_REGEX_LITERAL) {
      return MUST_NOT;
    }

    return LanguageUtil.canStickTokensTogetherByLexer(left, right, new GroovyLexer());
  }
}
