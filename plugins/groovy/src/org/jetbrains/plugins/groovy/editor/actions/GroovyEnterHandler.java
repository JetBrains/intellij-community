/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author ilyas
 */
public class GroovyEnterHandler extends EnterHandlerDelegateAdapter {

  private static final TokenSet GSTRING_TOKENS = TokenSet.create(GroovyTokenTypes.mGSTRING_BEGIN, GroovyTokenTypes.mGSTRING_CONTENT,
                                                                 GroovyTokenTypes.mGSTRING_END, GroovyTokenTypes.mGSTRING_LITERAL);

  private static final TokenSet REGEX_TOKENS = TokenSet.create(GroovyTokenTypes.mREGEX_BEGIN, GroovyTokenTypes.mREGEX_CONTENT,
                                                               GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
                                                               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
                                                               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END);

  private static final TokenSet AFTER_DOLLAR = TokenSet.create(GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mIDENT, GroovyTokenTypes.mDOLLAR,
                                                               GroovyTokenTypes.mGSTRING_END, GroovyTokenTypes.mREGEX_END,
                                                               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END,
                                                               GroovyElementTypes.GSTRING_CONTENT, GroovyTokenTypes.mGSTRING_CONTENT,
                                                               GroovyTokenTypes.mREGEX_CONTENT,
                                                               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT);

  private static final TokenSet ALL_STRINGS = TokenSet.create(GroovyTokenTypes.mSTRING_LITERAL, GroovyTokenTypes.mGSTRING_LITERAL,
                                                              GroovyTokenTypes.mGSTRING_BEGIN, GroovyTokenTypes.mGSTRING_END,
                                                              GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mRCURLY,
                                                              GroovyTokenTypes.mIDENT, GroovyTokenTypes.mDOLLAR,
                                                              GroovyTokenTypes.mREGEX_BEGIN, GroovyTokenTypes.mREGEX_CONTENT,
                                                              GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
                                                              GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
                                                              GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END, GroovyTokenTypes.mREGEX_LITERAL,
                                                              GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL,
                                                              GroovyElementTypes.GSTRING_CONTENT);

  private static final TokenSet BEFORE_DOLLAR =TokenSet.create(GroovyTokenTypes.mGSTRING_BEGIN, GroovyTokenTypes.mREGEX_BEGIN,
                                                               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
                                                               GroovyElementTypes.GSTRING_CONTENT,
                                                               GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mREGEX_CONTENT,
                                                               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT);

  private static final TokenSet EXPR_END = TokenSet.create(GroovyTokenTypes.mRCURLY, GroovyTokenTypes.mIDENT);

  private static final TokenSet AFTER_EXPR_END = TokenSet.create(GroovyTokenTypes.mGSTRING_END, GroovyTokenTypes.mDOLLAR,
                                                                 GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END,
                                                                 GroovyElementTypes.GSTRING_CONTENT,
                                                                 GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mREGEX_CONTENT,
                                                                 GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT);

  private static final TokenSet STRING_END = TokenSet.create(GroovyTokenTypes.mSTRING_LITERAL, GroovyTokenTypes.mGSTRING_LITERAL,
                                                             GroovyTokenTypes.mGSTRING_END, GroovyTokenTypes.mREGEX_END,
                                                             GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END, GroovyTokenTypes.mREGEX_LITERAL,
                                                             GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL);

  private static final TokenSet INNER_STRING_TOKENS = TokenSet.create(GroovyTokenTypes.mGSTRING_BEGIN, GroovyTokenTypes.mGSTRING_CONTENT,
                                                                      GroovyTokenTypes.mGSTRING_END, GroovyTokenTypes.mREGEX_BEGIN,
                                                                      GroovyTokenTypes.mREGEX_CONTENT, GroovyTokenTypes.mREGEX_END,
                                                                      GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
                                                                      GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
                                                                      GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END,
                                                                      GroovyElementTypes.GSTRING_INJECTION,
                                                                      GroovyElementTypes.GSTRING_CONTENT);

  public static void insertSpacesByGroovyContinuationIndent(Editor editor, Project project) {
    int indentSize = CodeStyle.getSettings(editor).getContinuationIndentSize(GroovyFileType.GROOVY_FILE_TYPE);
    EditorModificationUtil.insertStringAtCaret(editor, StringUtil.repeatSymbol(' ', indentSize));
  }


  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    Document document = editor.getDocument();
    Project project = file.getProject();
    CaretModel caretModel = editor.getCaretModel();

    if (!(file instanceof GroovyFileBase)) {
      return Result.Continue;
    }

    int docLength = document.getTextLength();
    if (docLength == 0) {
      return Result.Continue;
    }

    final int caret = caretModel.getOffset();
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    if (caret >= 1 && caret < docLength && CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
      HighlighterIterator iterator = highlighter.createIterator(caret);
      iterator.retreat();
      while (!iterator.atEnd() && TokenType.WHITE_SPACE == iterator.getTokenType()) {
        iterator.retreat();
      }
      boolean afterArrow = !iterator.atEnd() && iterator.getTokenType() == GroovyTokenTypes.mCLOSABLE_BLOCK_OP;
      if (afterArrow) {
        originalHandler.execute(editor, dataContext);
        PsiDocumentManager.getInstance(project).commitDocument(document);
        CodeStyleManager.getInstance(project).adjustLineIndent(file, caretModel.getOffset());
      }

      iterator = highlighter.createIterator(caretModel.getOffset());
      while (!iterator.atEnd() && TokenType.WHITE_SPACE == iterator.getTokenType()) {
        iterator.advance();
      }
      if (!iterator.atEnd() && GroovyTokenTypes.mRCURLY == iterator.getTokenType()) {
        PsiDocumentManager.getInstance(project).commitDocument(document);
        final PsiElement element = file.findElementAt(iterator.getStart());
        if (element != null &&
            element.getNode().getElementType() == GroovyTokenTypes.mRCURLY &&
            element.getParent() instanceof GrClosableBlock &&
            docLength > caret && afterArrow) {
          return Result.DefaultForceIndent;
        }
      }
      if (afterArrow) {
        return Result.Stop;
      }

      if (editor.isInsertMode() &&
          !HandlerUtils.isReadOnly(editor) &&
          !editor.getSelectionModel().hasSelection() &&
          handleFlyingGeese(editor, caret, dataContext, originalHandler, file)) {
        return Result.DefaultForceIndent;
      }
    }

    if (handleEnter(editor, dataContext, project, originalHandler)) return Result.Stop;
    return Result.Continue;
  }

  protected static boolean handleEnter(Editor editor,
                                       DataContext dataContext,
                                       @NotNull Project project,
                                       EditorActionHandler originalHandler) {
    if (HandlerUtils.isReadOnly(editor)) {
      return false;
    }
    int caretOffset = editor.getCaretModel().getOffset();
    if (caretOffset < 1) return false;

    if (handleBetweenSquareBraces(editor, caretOffset, dataContext, project, originalHandler)) {
      return true;
    }
    if (handleInString(editor, caretOffset, dataContext, originalHandler)) {
      return true;
    }

    return false;
  }

  private static boolean handleFlyingGeese(Editor editor,
                                           int caretOffset,
                                           DataContext dataContext,
                                           EditorActionHandler originalHandler,
                                           PsiFile file) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;

    GroovyCodeStyleSettings codeStyleSettings = GroovyCodeStyleSettings.getInstance(editor);
    if (!codeStyleSettings.USE_FLYING_GEESE_BRACES) return false;

    PsiElement element = file.findElementAt(caretOffset);
    if (element != null && element.getNode().getElementType() == TokenType.WHITE_SPACE) {
      element = GeeseUtil.getNextNonWhitespaceToken(element);
    }
    if (element == null || !GeeseUtil.isClosureRBrace(element)) return false;

    element = GeeseUtil.getNextNonWhitespaceToken(element);
    if (element == null || element.getNode().getElementType() != GroovyTokenTypes.mNLS || StringUtil.countChars(element.getText(), '\n') > 1) {
      return false;
    }

    element = GeeseUtil.getNextNonWhitespaceToken(element);
    if (element == null || !GeeseUtil.isClosureRBrace(element)) return false;

    Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    int toRemove = element.getTextRange().getStartOffset();
    document.deleteString(caretOffset + 1, toRemove);

    originalHandler.execute(editor, dataContext);

    String text = document.getText();
    int nextLineFeed = text.indexOf('\n', caretOffset + 1);
    if (nextLineFeed == -1) nextLineFeed = text.length();
    CodeStyleManager.getInstance(project).reformatText(file, caretOffset, nextLineFeed);

    return true;
  }

  private static boolean handleBetweenSquareBraces(Editor editor,
                                                   int caret,
                                                   DataContext context,
                                                   Project project,
                                                   EditorActionHandler originalHandler) {
    String text = editor.getDocument().getText();
    if (text == null || text.isEmpty()) return false;
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    if (caret < 1 || caret > text.length() - 1) {
      return false;
    }
    HighlighterIterator iterator = highlighter.createIterator(caret - 1);
    if (GroovyTokenTypes.mLBRACK == iterator.getTokenType()) {
      if (text.length() > caret) {
        iterator = highlighter.createIterator(caret);
        if (GroovyTokenTypes.mRBRACK == iterator.getTokenType()) {
          originalHandler.execute(editor, context);
          originalHandler.execute(editor, context);
          editor.getCaretModel().moveCaretRelatively(0, -1, false, false, true);
          insertSpacesByGroovyContinuationIndent(editor, project);
          return true;
        }
      }
    }
    return false;
  }

  private static boolean handleInString(Editor editor, int caretOffset, DataContext dataContext, EditorActionHandler originalHandler) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;

    final VirtualFile vfile = FileDocumentManager.getInstance().getFile(editor.getDocument());
    assert vfile != null;
    PsiFile file = PsiManager.getInstance(project).findFile(vfile);

    Document document = editor.getDocument();
    String fileText = document.getText();
    if (fileText.length() == caretOffset) return false;

    if (!checkStringApplicable(editor, caretOffset)) return false;
    if (file == null) return false;

    PsiDocumentManager.getInstance(project).commitDocument(document);

    final PsiElement stringElement = inferStringPair(file, caretOffset);
    if (stringElement == null) return false;

    ASTNode node = stringElement.getNode();
    final IElementType nodeElementType = node.getElementType();

    boolean isInsertIndent = isInsertIndent(caretOffset, stringElement.getTextRange().getStartOffset(), fileText);

    // For simple String literals like 'abc'
    CaretModel caretModel = editor.getCaretModel();
    if (nodeElementType == GroovyTokenTypes.mSTRING_LITERAL) {
      if (isSingleQuoteString(stringElement)) {

        //the case of print '\<caret>'
        if (isSlashBeforeCaret(caretOffset, fileText)) {
          EditorModificationUtil.insertStringAtCaret(editor, "\n");
        }
        else if(stringElement.getParent() instanceof GrReferenceExpression) {
          TextRange range = stringElement.getTextRange();
          convertEndToMultiline(range.getEndOffset(), document, fileText, '\'');
          document.insertString(range.getStartOffset(), "''");
          caretModel.moveToOffset(caretOffset + 2);
          EditorModificationUtil.insertStringAtCaret(editor, "\n");
        }
        else {
          EditorModificationUtil.insertStringAtCaret(editor, "'+");
          originalHandler.execute(editor, dataContext);
          EditorModificationUtil.insertStringAtCaret(editor, "'");
          PsiDocumentManager.getInstance(project).commitDocument(document);
          CodeStyleManager.getInstance(project).reformatRange(file, caretOffset, caretModel.getOffset());
        }
      }
      else {
        insertLineFeedInString(editor, dataContext, originalHandler, isInsertIndent);
      }
      return true;
    }

    if (GSTRING_TOKENS.contains(nodeElementType) ||
        nodeElementType == GroovyElementTypes.GSTRING_CONTENT && GSTRING_TOKENS.contains(node.getFirstChildNode().getElementType()) ||
        nodeElementType == GroovyTokenTypes.mDOLLAR && node.getTreeParent().getTreeParent().getElementType() == GroovyElementTypes.GSTRING) {
      PsiElement parent = stringElement.getParent();
      if (nodeElementType == GroovyTokenTypes.mGSTRING_LITERAL) {
        parent = stringElement;
      }
      else {
        while (parent != null && !(parent instanceof GrLiteral)) {
          parent = parent.getParent();
        }
      }
      if (parent == null) return false;
      if (isDoubleQuotedString(parent)) {
        PsiElement exprSibling = stringElement.getNextSibling();
        boolean rightFromDollar = exprSibling instanceof GrExpression && exprSibling.getTextRange().getStartOffset() == caretOffset;
        if (rightFromDollar) caretOffset--;
        TextRange parentRange = parent.getTextRange();
        if (rightFromDollar || parent.getParent() instanceof GrReferenceExpression) {
          convertEndToMultiline(parent.getTextRange().getEndOffset(), document, fileText, '"');
          document.insertString(parentRange.getStartOffset(), "\"\"");
          caretModel.moveToOffset(caretOffset + 2);
          EditorModificationUtil.insertStringAtCaret(editor, "\n");
          if (rightFromDollar) {
            caretModel.moveCaretRelatively(1, 0, false, false, true);
          }
        }
        else if (isSlashBeforeCaret(caretOffset, fileText)) {
          EditorModificationUtil.insertStringAtCaret(editor, "\n");
        }
        else {
          EditorModificationUtil.insertStringAtCaret(editor, "\"+");
          originalHandler.execute(editor, dataContext);
          EditorModificationUtil.insertStringAtCaret(editor, "\"");
          PsiDocumentManager.getInstance(project).commitDocument(document);
          CodeStyleManager.getInstance(project).reformatRange(file, caretOffset, caretModel.getOffset());
        }
      }
      else {
        insertLineFeedInString(editor, dataContext, originalHandler, isInsertIndent);
      }
      return true;
    }

    if (REGEX_TOKENS.contains(nodeElementType) ||
        nodeElementType == GroovyElementTypes.GSTRING_CONTENT && REGEX_TOKENS.contains(node.getFirstChildNode().getElementType()) ||
        nodeElementType == GroovyTokenTypes.mDOLLAR && node.getTreeParent().getTreeParent().getElementType() == GroovyElementTypes.REGEX) {
      PsiElement parent = stringElement.getParent();
      if (nodeElementType == GroovyTokenTypes.mREGEX_LITERAL || nodeElementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
        parent = stringElement;
      }
      else {
        while (parent != null && !(parent instanceof GrLiteral)) {
          parent = parent.getParent();
        }
      }
      if (parent == null || parent.getLastChild() instanceof PsiErrorElement) return false;
      PsiElement exprSibling = stringElement.getNextSibling();
      boolean rightFromDollar = exprSibling instanceof GrExpression && exprSibling.getTextRange().getStartOffset() == caretOffset;
      if (rightFromDollar) {
        caretModel.moveToOffset(caretOffset - 1);
      }
      insertLineFeedInString(editor, dataContext, originalHandler, isInsertIndent);
      if (rightFromDollar) {
        caretModel.moveCaretRelatively(1, 0, false, false, true);
      }
      return true;
    }

    return false;
  }

  private static boolean isDoubleQuotedString(PsiElement element) {
    return "\"".equals(GrStringUtil.getStartQuote(element.getText()));
  }

  private static boolean isSingleQuoteString(PsiElement element) {
    return "'".equals(GrStringUtil.getStartQuote(element.getText()));
  }

  @Nullable
  private static PsiElement inferStringPair(PsiFile file, int caretOffset) {
    PsiElement stringElement = file.findElementAt(caretOffset - 1);
    if (stringElement == null) return null;
    ASTNode node = stringElement.getNode();
    if (node == null) return null;

    // For expression injection in GString like "abc ${}<caret>  abc"
    if (!INNER_STRING_TOKENS.contains(node.getElementType()) && checkGStringInjection(stringElement)) {
      stringElement = stringElement.getParent().getParent().getNextSibling();
      if (stringElement == null) return null;
    }

    return stringElement;
  }

  private static boolean isSlashBeforeCaret(int caretOffset, String fileText) {
    return caretOffset > 0 && fileText.charAt(caretOffset - 1) == '\\';
  }

  private static void insertLineFeedInString(Editor editor,
                                             DataContext dataContext,
                                             EditorActionHandler originalHandler,
                                             boolean isInsertIndent) {
    if (isInsertIndent) {
      originalHandler.execute(editor, dataContext);
    }
    else {
      EditorModificationUtil.insertStringAtCaret(editor, "\n");
    }
  }

  private static boolean isInsertIndent(int caret, int stringOffset, String text) {
    final int i = text.indexOf('\n', stringOffset);
    return stringOffset < i && i < caret;
  }

  private static void convertEndToMultiline(int caretOffset, Document document, String fileText, char c) {
    if (caretOffset < fileText.length() && fileText.charAt(caretOffset) == c ||
        caretOffset > 0 && fileText.charAt(caretOffset - 1) == c) {
      document.insertString(caretOffset, new CharArrayCharSequence(c, c));
    }
    else {
      document.insertString(caretOffset, new CharArrayCharSequence(c, c, c));
    }
  }

  private static boolean checkStringApplicable(Editor editor, int caret) {
    final GroovyLexer lexer = new GroovyLexer();
    lexer.start(editor.getDocument().getText());

    while (lexer.getTokenEnd() < caret) {
      lexer.advance();
    }
    final IElementType leftToken = lexer.getTokenType();
    if (lexer.getTokenEnd() <= caret) lexer.advance();
    final IElementType rightToken = lexer.getTokenType();

    if (!(ALL_STRINGS.contains(leftToken))) {
      return false;
    }
    if (BEFORE_DOLLAR.contains(leftToken) && !AFTER_DOLLAR.contains(rightToken)) {
      return false;
    }
    if (EXPR_END.contains(leftToken) && !AFTER_EXPR_END.contains(rightToken)) {
      return false;
    }
    if (STRING_END.contains(leftToken) && !STRING_END.contains(rightToken)) {
      return false;
    }
    return true;
  }

  private static boolean checkGStringInjection(PsiElement element) {
    if (element != null && (element.getParent() instanceof GrReferenceExpression || element.getParent() instanceof GrClosableBlock)) {
      final PsiElement parent = element.getParent().getParent();
      if (!(parent instanceof GrStringInjection)) return false;
      PsiElement nextSibling = parent.getNextSibling();
      if (nextSibling == null) return false;
      return INNER_STRING_TOKENS.contains(nextSibling.getNode().getElementType());
    }
    return false;
  }
}
