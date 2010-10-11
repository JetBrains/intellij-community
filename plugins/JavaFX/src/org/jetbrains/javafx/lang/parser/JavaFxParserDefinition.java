package org.jetbrains.javafx.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.JavaFxElementType;
import org.jetbrains.javafx.lang.lexer.JavaFxFlexLexer;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.psi.impl.JavaFxFileImpl;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxStubElementType;

/**
 * Defines parser & lexer implementations for the plugin
 *
 * @author andrey, Alexey.Ivanov
 */
public class JavaFxParserDefinition implements ParserDefinition {

  @NotNull
  public Lexer createLexer(Project project) {
    return new JavaFxFlexLexer();
  }

  public PsiParser createParser(Project project) {
    return new JavaFxParser();
  }

  public IFileElementType getFileNodeType() {
    return JavaFxStubElementTypes.FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return JavaFxTokenTypes.WHITESPACES;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return JavaFxTokenTypes.COMMENTS;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return JavaFxTokenTypes.STRINGS;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof JavaFxElementType) {
      final JavaFxElementType javaFxElementType = (JavaFxElementType)type;
      return javaFxElementType.createElement(node);
    }
    else if (type instanceof JavaFxStubElementType) {
      return ((JavaFxStubElementType)type).createElement(node);
    }

    return new ASTWrapperPsiElement(node);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new JavaFxFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return LanguageUtil.canStickTokensTogetherByLexer(left, right, new JavaFxFlexLexer());
  }
}
