/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant;

import com.intellij.lang.*;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class AntParserDefinition implements ParserDefinition {

  private final ParserDefinition myXmlParserDef;

  public AntParserDefinition() {
    myXmlParserDef = LanguageParserDefinitions.INSTANCE.forLanguage(StdLanguages.XML);
  }

  @NotNull
  public Lexer createLexer(Project project) {
    return myXmlParserDef.createLexer(project);
  }

  @NotNull
  public PsiParser createParser(Project project) {
    return myXmlParserDef.createParser(project);
  }

  public IFileElementType getFileNodeType() {
    return myXmlParserDef.getFileNodeType();
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return myXmlParserDef.getWhitespaceTokens();
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return myXmlParserDef.getCommentTokens();
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    return myXmlParserDef.createElement(node);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new AntFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
