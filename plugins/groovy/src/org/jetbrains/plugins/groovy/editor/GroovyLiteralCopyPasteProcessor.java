// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.editor;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.editorActions.StringLiteralCopyPasteProcessor;
import com.intellij.lang.ASTNode;
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
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind;
import org.jetbrains.plugins.groovy.lang.psi.util.StringUtilKt;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyStringLiteralManipulator;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

public class GroovyLiteralCopyPasteProcessor extends StringLiteralCopyPasteProcessor {

  @Nullable
  @Override
  protected TextRange getEscapedRange(@NotNull PsiElement token) {
    final ASTNode node = token.getNode();
    if (node == null) return null;

    final IElementType tokenType = node.getElementType();
    if (GroovyTokenSets.STRING_LITERALS.contains(tokenType)) {
      final String text = token.getText();
      if (text == null) return null;
      return GroovyStringLiteralManipulator.getLiteralRange(text).shiftRight(node.getStartOffset());
    }
    if (tokenType == SLASHY_CONTENT || tokenType == DOLLAR_SLASHY_CONTENT) {
      return token.getTextRange();
    }
    return null;
  }

  @Nullable
  @Override
  protected String unescape(String s, PsiElement token) {
    StringKind stringKind = getStringKindByToken(token);
    return stringKind == null ? null : stringKind.unescape(s);
  }

  @NotNull
  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (!isSupportedFile(file)) {
      return text;
    }

    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    final SelectionModel selectionModel = editor.getSelectionModel();

    // pastes in block selection mode (column mode) are not handled by a CopyPasteProcessor
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();

    final StringKind stringKind = findStringKind(file, selectionStart, selectionEnd);
    if (stringKind == null) {
      return text;
    }

    if (rawText != null && canPasteRaw(text, rawText.rawText, stringKind)) {
      return rawText.rawText;
    }

    StringBuilder buffer = new StringBuilder(text.length());
    @NonNls String breaker = stringKind.getLineBreaker();
    final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      buffer.append(escape(stringKind, line));
      if (i != lines.length - 1) {
        buffer.append(breaker);
      }
      else if (text.endsWith("\n")) {
        buffer.append(stringKind.escape("\n"));
      }
    }
    return buffer.toString();
  }

  @Override
  protected boolean isSupportedFile(PsiFile file) {
    return file instanceof GroovyFile;
  }

  private static boolean canPasteRaw(String text, String rawText, StringKind kind) {
    if (!text.equals(kind.unescape(rawText))) {
      return false;
    }
    if (kind == StringKind.SINGLE_QUOTED) {
      return StringUtilKt.isValidSingleQuotedStringContent(rawText);
    }
    else if (kind == StringKind.DOUBLE_QUOTED) {
      return StringUtilKt.isValidDoubleQuotedStringContent(rawText);
    }
    else {
      return true;
    }
  }

  @NotNull
  private static String escape(@NotNull StringKind kind, @NotNull String s) {
    if (s.isEmpty()) {
      return s;
    }
    if (kind != StringKind.DOUBLE_QUOTED && kind != StringKind.TRIPLE_DOUBLE_QUOTED) {
      return kind.escape(s);
    }
    boolean singleLine = kind == StringKind.DOUBLE_QUOTED;
    StringBuilder b = new StringBuilder(s.length());
    GrStringUtil.escapeStringCharacters(s.length(), s, singleLine ? "\"" : "", singleLine, true, b);
    GrStringUtil.unescapeCharacters(b, singleLine ? "'" : "'\"", true);
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

  @VisibleForTesting
  @Nullable
  public static StringKind findStringKind(PsiFile file, int startOffset, int endOffset) {
    if (startOffset == endOffset) {
      return findStringKind(file, startOffset);
    }
    StringKind startKind = findStringKind(file, startOffset);
    StringKind endKind = findStringKind(file, endOffset);
    if (startKind == endKind) {
      return startKind;
    }
    return null;
  }

  @Nullable
  private static StringKind findStringKind(@NotNull PsiFile file, int offset) {
    final PsiElement leaf = file.findElementAt(offset);
    if (leaf == null) {
      return null;
    }
    final IElementType leafType = leaf.getNode().getElementType();
    final int leafOffset = leaf.getTextOffset();
    if (offset == leafOffset) {
      // special cases
      if (leafType == GSTRING_END || leafType == SLASHY_END || leafType == DOLLAR_SLASHY_END) {
        PsiElement prevSibling = leaf.getPrevSibling();
        if (prevSibling != null) {
          IElementType previousElementType = prevSibling.getNode().getElementType();
          if (previousElementType == STRING_INJECTION) {
            if (leafType == GSTRING_END) {
              if (isMultiline(leaf.getParent())) {
                // """...${}<here>"""
                return StringKind.TRIPLE_DOUBLE_QUOTED;
              }
              else {
                // "...${}<here>"
                return StringKind.DOUBLE_QUOTED;
              }
            }
            if (leafType == SLASHY_END) {
              // /...${}<here>/
              return StringKind.SLASHY;
            }
            else {
              // $/...${}<here>/$
              return StringKind.DOLLAR_SLASHY;
            }
          }
          else if (previousElementType == STRING_CONTENT) {
            // "...${}...<here>"
            // """...${}...<here>"""
            // /...${}...<here>/
            // $/...${}...<here>/$
            PsiElement contentLeaf = prevSibling.getFirstChild();
            return getStringKindByContentTokenType(contentLeaf, contentLeaf.getNode().getElementType());
          }
          else if (previousElementType == DOLLAR_SLASHY_BEGIN) {
            // $/<here>/$
            return StringKind.DOLLAR_SLASHY;
          }
          else if (leafType != GSTRING_END) {
            // /...<here>/
            // $/...<here>/$
            //
            // "..." is a single token returned by lexer,
            // while slashy and dollar slashy strings without injections consist of three tokens start quote, content and end quote
            return getStringKindByContentTokenType(leaf, previousElementType);
          }
        }
      }
      else if (leafType == T_DOLLAR) {
        // Strings with injections, leaf is dollar:
        // "<here>${}..." or "...<here>${}..."
        // /<here>${}.../ or /...<here>${}.../
        // $/<here>${}.../$ or $/...<here>${}.../$
        PsiElement parent = leaf.getParent(); // template string injection
        PsiElement gParent = parent.getParent(); // template string element;
        return getStringKindByStringElement(gParent);
      }
    }
    final int leafEndOffset = leafOffset + leaf.getTextLength();
    if (leafType == STRING_SQ || leafType == STRING_DQ) {
      if (leafOffset + 1 <= offset && offset <= leafEndOffset - 1) {
        // Single token literals:
        // '<here>......'
        // '...<here>...'
        // '......<here>'
        // "<here>......"
        // "...<here>..."
        // "......<here>"
        return leafType == STRING_SQ ? StringKind.SINGLE_QUOTED : StringKind.DOUBLE_QUOTED;
      }
    }
    if (leafType == STRING_TSQ || leafType == STRING_TDQ) {
      // Single token literals:
      // '''<here>...'''
      // """<here>..."""
      if (leafOffset + 3 <= offset && offset <= leafEndOffset - 3) {
        return leafType == STRING_TSQ ? StringKind.TRIPLE_SINGLE_QUOTED : StringKind.TRIPLE_DOUBLE_QUOTED;
      }
    }

    // Strings with injections, leaf is string content:
    // "<here>...${}..." or "...<here>...${}..."
    // """<here>...${}...""" or """...<here>...${}..."""
    // /<here>...${}.../ or /...<here>...${}.../
    // $/<here>...${}.../$ or $/...<here>...${}.../$
    return getStringKindByContentTokenType(leaf, leafType);
  }

  @Nullable
  private static StringKind getStringKindByStringElement(@NotNull PsiElement templateStringElement) {
    IElementType elementType = templateStringElement.getNode().getElementType();
    if (elementType == GSTRING) {
      return isMultiline(templateStringElement) ? StringKind.TRIPLE_DOUBLE_QUOTED : StringKind.DOUBLE_QUOTED;
    }
    else if (elementType == REGEX) {
      IElementType quoteType = templateStringElement.getFirstChild().getNode().getElementType();
      return quoteType == SLASHY_BEGIN ? StringKind.SLASHY : StringKind.DOLLAR_SLASHY;
    }
    else if (elementType == SLASHY_LITERAL) {
      return StringKind.SLASHY;
    }
    else if (elementType == DOLLAR_SLASHY_LITERAL) {
      return StringKind.DOLLAR_SLASHY;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static StringKind getStringKindByContentTokenType(PsiElement leaf, IElementType contentTokenType) {
    if (contentTokenType == GSTRING_CONTENT) {
      PsiElement parent = leaf.getParent(); // template string content
      PsiElement gParent = parent.getParent(); // template string element
      if (isMultiline(gParent)) {
        // "....${}...<here>"
        return StringKind.TRIPLE_DOUBLE_QUOTED;
      }
      else {
        // """....${}...<here>"""
        return StringKind.DOUBLE_QUOTED;
      }
    }
    else if (contentTokenType == SLASHY_CONTENT) {
      // /....${}...<here>/
      return StringKind.SLASHY;
    }
    else if (contentTokenType == DOLLAR_SLASHY_CONTENT) {
      // $/...${}...<here>/$
      return StringKind.DOLLAR_SLASHY;
    }
    return null;
  }

  private static boolean isMultiline(@NotNull PsiElement templateStringElement) {
    return templateStringElement.getFirstChild().textMatches("\"\"\"");
  }

  @Nullable
  private static StringKind getStringKindByToken(@NotNull PsiElement token) {
    IElementType leafType = token.getNode().getElementType();
    if (leafType == STRING_SQ) {
      return StringKind.SINGLE_QUOTED;
    }
    else if (leafType == STRING_DQ) {
      return StringKind.DOUBLE_QUOTED;
    }
    else if (leafType == STRING_TSQ) {
      return StringKind.TRIPLE_SINGLE_QUOTED;
    }
    else if (leafType == STRING_TDQ) {
      return StringKind.TRIPLE_DOUBLE_QUOTED;
    }
    return getStringKindByContentTokenType(token, leafType);
  }

  @Override
  protected String getLineBreaker(@NotNull PsiElement token) {
    throw new IllegalStateException("must not be called");
  }

  @NotNull
  @Override
  protected String escapeCharCharacters(@NotNull String s, @NotNull PsiElement token) {
    throw new IllegalStateException("must not be called");
  }

  @Override
  public String escapeAndSplit(String text, PsiElement token) {
    throw new IllegalStateException("must not be called");
  }

  @NotNull
  @Override
  protected String escapeTextBlock(@NotNull String text, int offset, boolean escapeStartQuote, boolean escapeEndQuote) {
    throw new IllegalStateException("must not be called");
  }

  @Override
  protected boolean isCharLiteral(@NotNull PsiElement token) {
    throw new IllegalStateException("must not be called");
  }

  @Override
  protected boolean isStringLiteral(@NotNull PsiElement token) {
    throw new IllegalStateException("must not be called");
  }

  @Override
  protected boolean isTextBlock(@NotNull PsiElement token) {
    throw new IllegalStateException("must not be called");
  }

  @Override
  @Nullable
  protected PsiElement findLiteralTokenType(PsiFile file, int selectionStart, int selectionEnd) {
    throw new IllegalStateException("must not be called");
  }
}
