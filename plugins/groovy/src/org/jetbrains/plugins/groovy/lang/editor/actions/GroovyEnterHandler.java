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

package org.jetbrains.plugins.groovy.lang.editor.actions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class GroovyEnterHandler implements EnterHandlerDelegate {

  public Result preprocessEnter(PsiFile file,
                                Editor editor,
                                Ref<Integer> caretOffset,
                                Ref<Integer> caretAdvance,
                                DataContext dataContext,
                                EditorActionHandler originalHandler) {
    String text = editor.getDocument().getText();
    if (StringUtil.isEmpty(text)) {
      return Result.Continue;
    }

    final int caret = editor.getCaretModel().getOffset();
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    if (caret >= 1 && caret < text.length() && CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
      HighlighterIterator iterator = highlighter.createIterator(caret);
      iterator.retreat();
      while (!iterator.atEnd() && mWS == iterator.getTokenType()) {
        iterator.retreat();
      }
      boolean afterArrow = !iterator.atEnd() && iterator.getTokenType() == mCLOSABLE_BLOCK_OP;
      if (afterArrow) {
        originalHandler.execute(editor, dataContext);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
        CodeStyleManager.getInstance(file.getProject()).adjustLineIndent(file, editor.getCaretModel().getOffset());
      }

      iterator = highlighter.createIterator(editor.getCaretModel().getOffset());
      while (!iterator.atEnd() && mWS == iterator.getTokenType()) {
        iterator.advance();
      }
      if (!iterator.atEnd() && mRCURLY == iterator.getTokenType()) {
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
        final PsiElement element = file.findElementAt(iterator.getStart());
        if (element != null &&
            element.getNode().getElementType() == mRCURLY &&
            element.getParent() instanceof GrClosableBlock &&
            text.length() > caret) {
          return Result.DefaultForceIndent;
        }
      }
      if (afterArrow) {
        return Result.Stop;
      }
    }

    if (handleEnter(editor, dataContext, file.getProject(), originalHandler, file)) {
      return Result.Stop;
    }
    return Result.Continue;
  }

  protected static boolean handleEnter(Editor editor,
                                       DataContext dataContext,
                                       @NotNull Project project,
                                       EditorActionHandler originalHandler, PsiFile file) {
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

  private static boolean handleBetweenSquareBraces(Editor editor,
                                                   int caret,
                                                   DataContext context,
                                                   Project project,
                                                   EditorActionHandler originalHandler) {
    String text = editor.getDocument().getText();
    if (text == null || text.length() == 0) return false;
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    if (caret < 1 || caret > text.length() - 1) {
      return false;
    }
    HighlighterIterator iterator = highlighter.createIterator(caret - 1);
    if (mLBRACK == iterator.getTokenType()) {
      if (text.length() > caret) {
        iterator = highlighter.createIterator(caret);
        if (mRBRACK == iterator.getTokenType()) {
          originalHandler.execute(editor, context);
          originalHandler.execute(editor, context);
          editor.getCaretModel().moveCaretRelatively(0, -1, false, false, true);
          GroovyEditorActionUtil.insertSpacesByGroovyContinuationIndent(editor, project);
          return true;
        }
      }
    }
    return false;
  }

  private static final TokenSet AFTER_DOLLAR = TokenSet.create(mLCURLY, mIDENT, mGSTRING_CONTENT, mDOLLAR, mGSTRING_END);

  private static final TokenSet ALL_STRINGS =
    TokenSet.create(mSTRING_LITERAL, mGSTRING_LITERAL, mGSTRING_BEGIN, mGSTRING_END, mGSTRING_CONTENT, mRCURLY, mIDENT, mDOLLAR);

  private static final TokenSet BEFORE_DOLLAR = TokenSet.create(mGSTRING_BEGIN, mGSTRING_CONTENT);

  private static final TokenSet EXPR_END = TokenSet.create(mRCURLY, mIDENT);

  private static final TokenSet AFTER_EXPR_END = TokenSet.create(mGSTRING_END, mGSTRING_CONTENT, mDOLLAR);

  private static final TokenSet STRING_END = TokenSet.create(mSTRING_LITERAL, mGSTRING_LITERAL, mGSTRING_END);


  private static boolean handleInString(Editor editor, int caretOffset, DataContext dataContext, EditorActionHandler originalHandler) {
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;

    PsiFile file = PsiManager.getInstance(project).findFile(FileDocumentManager.getInstance().getFile(editor.getDocument()));

    Document document = editor.getDocument();
    String fileText = document.getText();
    if (fileText.length() == caretOffset) return false;

    if (!checkStringApplicable(editor, caretOffset)) return false;
    if (file == null) return false;

    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiElement stringElement = file.findElementAt(caretOffset - 1);
    if (stringElement == null) return false;
    ASTNode node = stringElement.getNode();
    if (node == null) return false;

    // For simple String literals like 'abcdef'
    if (mSTRING_LITERAL == node.getElementType()) {
      if (GroovyEditorActionUtil.isPlainStringLiteral(node)) {
        String text = node.getText();
        String innerText = text.equals("''") ? "" : text.substring(1, text.length() - 1);
        TextRange literalRange = stringElement.getTextRange();
        document.replaceString(literalRange.getStartOffset(), literalRange.getEndOffset(), "'''" + innerText + "'''");
        editor.getCaretModel().moveToOffset(caretOffset + 2);
        EditorModificationUtil.insertStringAtCaret(editor, "\n");
      }
      else {
        EditorModificationUtil.insertStringAtCaret(editor, "\n");
      }
      return true;
    }

    // For expression injection in GString like "abc ${}<caret>  abc"
    if (!GroovyEditorActionUtil.GSTRING_TOKENS.contains(node.getElementType()) && checkGStringInnerExpression(stringElement)) {
      stringElement = stringElement.getParent().getParent().getNextSibling();
      if (stringElement == null) return false;
      node = stringElement.getNode();
      if (node == null) return false;
    }

    if (GroovyEditorActionUtil.GSTRING_TOKENS.contains(node.getElementType())) {
      PsiElement parent = stringElement.getParent();
      if (node.getElementType() == mGSTRING_LITERAL) {
        parent = stringElement;
      }
      else {
        while (parent != null && !(parent instanceof GrLiteral)) {
          parent = parent.getParent();
        }
      }
      if (parent == null || parent.getLastChild() instanceof PsiErrorElement) return false;
      if (GroovyEditorActionUtil.isPlainGString(parent.getNode())) {
        PsiElement exprSibling = stringElement.getNextSibling();
        boolean rightFromDollar = exprSibling instanceof GrExpression && exprSibling.getTextRange().getStartOffset() == caretOffset;
        if (rightFromDollar) caretOffset--;
        String text = parent.getText();
        String innerText = text.equals("\"\"") ? "" : text.substring(1, text.length() - 1);
        TextRange parentRange = parent.getTextRange();
        document.replaceString(parentRange.getStartOffset(), parentRange.getEndOffset(), "\"\"\"" + innerText + "\"\"\"");
        editor.getCaretModel().moveToOffset(caretOffset + 2);
        EditorModificationUtil.insertStringAtCaret(editor, "\n");
        if (rightFromDollar) {
          editor.getCaretModel().moveCaretRelatively(1, 0, false, false, true);
        }
      }
      else {
        originalHandler.execute(editor, dataContext);
      }
      return true;
    }
    return false;
  }

  private static boolean checkStringApplicable(Editor editor, int caret) {
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iteratorLeft = highlighter.createIterator(caret - 1);
    HighlighterIterator iteratorRight = highlighter.createIterator(caret);

    if (iteratorLeft != null && !(ALL_STRINGS.contains(iteratorLeft.getTokenType()))) {
      return false;
    }
    if (iteratorLeft != null &&
        BEFORE_DOLLAR.contains(iteratorLeft.getTokenType()) &&
        iteratorRight != null &&
        !AFTER_DOLLAR.contains(iteratorRight.getTokenType())) {
      return false;
    }
    if (iteratorLeft != null &&
        EXPR_END.contains(iteratorLeft.getTokenType()) &&
        iteratorRight != null &&
        !AFTER_EXPR_END.contains(iteratorRight.getTokenType())) {
      return false;
    }
    if (iteratorLeft != null &&
        STRING_END.contains(iteratorLeft.getTokenType()) &&
        iteratorRight != null &&
        !STRING_END.contains(iteratorRight.getTokenType())) {
      return false;
    }
    return true;
  }

  private static boolean checkGStringInnerExpression(PsiElement element) {
    if (element != null && (element.getParent() instanceof GrReferenceExpression || element.getParent() instanceof GrClosableBlock)) {
      final PsiElement parent = element.getParent().getParent();
      if (!(parent instanceof GrStringInjection)) return false;
      PsiElement nextSibling = parent.getNextSibling();
      if (nextSibling == null) return false;
      return GroovyEditorActionUtil.GSTRING_TOKENS_INNER.contains(nextSibling.getNode().getElementType());
    }
    return false;
  }
}
