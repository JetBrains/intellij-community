package com.jetbrains.gettext.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.gettext.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextParserDefinition implements ParserDefinition {
  private static final TokenSet WHITE_SPACE = TokenSet.create(GetTextTokenTypes.WHITE_SPACE);
  private static final TokenSet COMMENT = TokenSet.create(GetTextTokenTypes.COMMENT, GetTextTokenTypes.COMMENT_SYMBOLS);

  @NotNull
  public Lexer createLexer(Project project) {
    return new GetTextLexer();
  }

  public PsiParser createParser(Project project) {
    return new GetTextParser();
  }

  public IFileElementType getFileNodeType() {
    return new IStubFileElementType(GetTextLanguage.INSTANCE);
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return WHITE_SPACE;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return COMMENT;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return GetTextTokenTypes.STRING_LITERALS;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof GetTextCompositeElementType) {
      return GetTextCompositeElementType.createPsiElement(node);
    }
    throw new AssertionError("Unknown type: " + type);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new GetTextFile(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }

  @Override
  public String toString() {
    return "GetTextParserDefinition";
  }
}

