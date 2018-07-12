// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.editor;

import com.intellij.codeInsight.editorActions.StringLiteralCopyPasteProcessor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyStringLiteralManipulator;

/**
 * @author peter
 */
public class GroovyLiteralCopyPasteProcessor extends StringLiteralCopyPasteProcessor {

  private static final Logger LOG = Logger.getInstance(GroovyLiteralCopyPasteProcessor.class);

  @Override
  protected boolean isCharLiteral(@NotNull PsiElement token) {
    return false;
  }

  @Override
  protected boolean isStringLiteral(@NotNull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null &&
           (TokenSets.STRING_LITERALS.contains(node.getElementType()) ||
            node.getElementType() == GroovyElementTypes.GSTRING_INJECTION ||
            node.getElementType() == GroovyElementTypes.GSTRING_CONTENT);
  }

  @Nullable
  @Override
  protected TextRange getEscapedRange(@NotNull PsiElement token) {
    final ASTNode node = token.getNode();
    if (node == null) return null;

    final IElementType tokenType = node.getElementType();
    if (tokenType == GroovyTokenTypes.mSTRING_LITERAL || tokenType == GroovyTokenTypes.mGSTRING_LITERAL) {
      final String text = token.getText();
      if (text == null) return null;
      return GroovyStringLiteralManipulator.getLiteralRange(text).shiftRight(node.getStartOffset());
    }
    if (tokenType == GroovyTokenTypes.mREGEX_CONTENT) {
      return token.getTextRange();
    }
    return null;
  }

  @Override
  @Nullable
  protected PsiElement findLiteralTokenType(PsiFile file, int selectionStart, int selectionEnd) {
    PsiElement elementAtSelectionStart = file.findElementAt(selectionStart);
    if (elementAtSelectionStart == null) {
      return null;
    }
    IElementType elementType = elementAtSelectionStart.getNode().getElementType();
    if ((elementType == GroovyTokenTypes.mREGEX_END || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END || elementType ==
                                                                                                                  GroovyTokenTypes.mGSTRING_END) &&
        elementAtSelectionStart.getTextOffset() == selectionStart) {
      elementAtSelectionStart = elementAtSelectionStart.getPrevSibling();
      if (elementAtSelectionStart == null) return null;
      elementType = elementAtSelectionStart.getNode().getElementType();
    }
    if (elementType == GroovyTokenTypes.mDOLLAR) {
      elementAtSelectionStart = elementAtSelectionStart.getParent();
      elementType = elementAtSelectionStart.getNode().getElementType();
    }

    if (!isStringLiteral(elementAtSelectionStart)) {
      return null;
    }

    if (elementAtSelectionStart.getTextRange().getEndOffset() < selectionEnd) {
      final PsiElement elementAtSelectionEnd = file.findElementAt(selectionEnd);
      if (elementAtSelectionEnd == null) {
        return null;
      }
      if (elementAtSelectionEnd.getNode().getElementType() == elementType &&
          elementAtSelectionEnd.getTextRange().getStartOffset() < selectionEnd) {
        return elementAtSelectionStart;
      }
    }

    final TextRange textRange = elementAtSelectionStart.getTextRange();

    //content elements don't have quotes, so they are shorter than whole string literals
    if (elementType == GroovyTokenTypes.mREGEX_CONTENT ||
        elementType == GroovyTokenTypes.mGSTRING_CONTENT ||
        elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT ||
        elementType == GroovyElementTypes.GSTRING_INJECTION) {
      selectionStart++;
      selectionEnd--;
    }
    if (textRange.getLength() > 0 && (selectionStart <= textRange.getStartOffset() || selectionEnd >= textRange.getEndOffset())) {
      return null;
    }

    if (elementType == GroovyElementTypes.GSTRING_CONTENT) {
      elementAtSelectionStart = elementAtSelectionStart.getFirstChild();
    }

    return elementAtSelectionStart;
  }


  @Override
  protected String getLineBreaker(@NotNull PsiElement token) {
    PsiElement parent = GrStringUtil.findContainingLiteral(token);
    final String text = parent.getText();
    if (text.contains("'''") || text.contains("\"\"\"")) {
      return "\n";
    }

    final IElementType type = token.getNode().getElementType();
    if (type == GroovyTokenTypes.mGSTRING_LITERAL || type == GroovyTokenTypes.mGSTRING_CONTENT) {
      return super.getLineBreaker(token);
    }
    if (type == GroovyTokenTypes.mSTRING_LITERAL) {
      return super.getLineBreaker(token).replace('"', '\'');
    }

    return "\n";

  }

  @NotNull
  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    final SelectionModel selectionModel = editor.getSelectionModel();

    // pastes in block selection mode (column mode) are not handled by a CopyPasteProcessor
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    PsiElement token = findLiteralTokenType(file, selectionStart, selectionEnd);
    if (token == null) {
      return text;
    }

    if (isStringLiteral(token)) {
      StringBuilder buffer = new StringBuilder(text.length());
      @NonNls String breaker = getLineBreaker(token);
      final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
      for (int i = 0; i < lines.length; i++) {
        buffer.append(escapeCharCharacters(lines[i], token));
        if (i != lines.length - 1 || "\n".equals(breaker) && text.endsWith("\n")) {
          buffer.append(breaker);
        }
      }
      text = buffer.toString();
    }
    return text;
  }

  @NotNull
  @Override
  protected String escapeCharCharacters(@NotNull String s, @NotNull PsiElement token) {
    if (s.isEmpty()) return s;
    IElementType tokenType = token.getNode().getElementType();

    if (tokenType == GroovyTokenTypes.mREGEX_CONTENT || tokenType == GroovyTokenTypes.mREGEX_LITERAL) {
      return GrStringUtil.escapeSymbolsForSlashyStrings(s);
    }

    if (tokenType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT || tokenType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      return GrStringUtil.escapeSymbolsForDollarSlashyStrings(s);
    }

    if (tokenType == GroovyTokenTypes.mGSTRING_CONTENT || tokenType == GroovyTokenTypes.mGSTRING_LITERAL || tokenType == GroovyElementTypes.GSTRING_INJECTION) {
      boolean singleLine = !GrStringUtil.findContainingLiteral(token).getText().contains("\"\"\"");
      StringBuilder b = new StringBuilder();
      GrStringUtil.escapeStringCharacters(s.length(), s, singleLine ? "\"" : "", singleLine, true, b);
      GrStringUtil.unescapeCharacters(b, singleLine ? "'" : "'\"", true);
      LOG.assertTrue(b.length() > 0, "s=" + s);
      for (int i = b.length() - 2; i >= 0; i--) {
        if (b.charAt(i) == '$') {
          final char next = b.charAt(i + 1);
          if (next != '{' && !Character.isLetter(next)) {
            b.insert(i, '\\');
          }
        }
      }
      if (b.charAt(b.length() - 1) == '$') {
        b.insert(b.length() - 1, '\\');
      }
      return b.toString();
    }

    if (tokenType == GroovyTokenTypes.mSTRING_LITERAL) {
      return GrStringUtil.escapeSymbolsForString(s, !token.getText().contains("'''"), false);
    }

    return super.escapeCharCharacters(s, token);
  }

  @NotNull
  @Override
  protected String unescape(String s, PsiElement token) {
    final IElementType tokenType = token.getNode().getElementType();

    if (tokenType == GroovyTokenTypes.mREGEX_CONTENT || tokenType == GroovyTokenTypes.mREGEX_LITERAL) {
      return GrStringUtil.unescapeSlashyString(s);
    }

    if (tokenType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT || tokenType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      return GrStringUtil.unescapeDollarSlashyString(s);
    }

    if (tokenType == GroovyTokenTypes.mGSTRING_CONTENT || tokenType == GroovyTokenTypes.mGSTRING_LITERAL) {
      return GrStringUtil.unescapeString(s);
    }

    if (tokenType == GroovyTokenTypes.mSTRING_LITERAL) {
      return GrStringUtil.unescapeString(s);
    }

    return super.unescape(s, token);
  }

}
