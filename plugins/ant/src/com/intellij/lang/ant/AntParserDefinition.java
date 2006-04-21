package com.intellij.lang.ant;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
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

  AntParserDefinition(ParserDefinition xmlParserDefinition) {
    myXmlParserDef = xmlParserDefinition;
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
