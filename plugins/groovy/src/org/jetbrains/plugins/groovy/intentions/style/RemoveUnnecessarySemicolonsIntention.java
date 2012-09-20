/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class RemoveUnnecessarySemicolonsIntention implements IntentionAction {
  @NotNull
  @Override
  public String getText() {
    return GroovyIntentionsBundle.message("remove.unnecessary.semicolons.name");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("remove.unnecessary.semicolons.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!(file instanceof GroovyFileBase)) return false;
    if (selectionModel.hasBlockSelection()) return false;

    if (selectionModel.hasSelection()) {
      final HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(selectionModel.getSelectionStart());
      final int end = selectionModel.getSelectionEnd();
      while (!iterator.atEnd()) {
        if (iterator.getTokenType() == GroovyTokenTypes.mSEMI) return true;
        if (iterator.getStart() > end) return false;
        iterator.advance();
      }
      return false;
    }

    int offset = editor.getCaretModel().getOffset();
    if (offset >= editor.getDocument().getTextLength()) offset = editor.getDocument().getTextLength() - 1;
    final PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    if (element.getNode().getElementType() == GroovyTokenTypes.mSEMI) return true;

    final PsiElement next = PsiTreeUtil.nextLeaf(element);
    if (next != null && next.getNode().getElementType() == GroovyTokenTypes.mSEMI) return true;


    final PsiElement prev = PsiTreeUtil.prevLeaf(element);
    if (prev != null && prev.getNode().getElementType() == GroovyTokenTypes.mSEMI) return true;

    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasBlockSelection()) return;

    Document document = editor.getDocument();
    if (selectionModel.hasSelection()) {
      final int start = selectionModel.getSelectionStart();
      final int end = selectionModel.getSelectionEnd();
      final TextRange range = new TextRange(start, end);

      final ArrayList<PsiElement> colons = new ArrayList<PsiElement>();
      file.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (!range.intersects(element.getTextRange())) return;

          final IElementType elementType = element.getNode().getElementType();
          if (elementType == GroovyTokenTypes.mSEMI) {
            colons.add(element);
          }
          else {
            super.visitElement(element);
          }
        }
      });

      boolean removed = false;
      for (PsiElement colon : colons) {
        final boolean b = checkAndRemove(project, colon, document);
        removed = removed || b;
      }

      if (!removed) {
        CommonRefactoringUtil.showErrorHint(project, editor, GroovyIntentionsBundle.message("no.unnecessary.semicolons.found"),
                                            GroovyIntentionsBundle.message("remove.unnecessary.semicolons.name"), null);
      }
    }
    else {
      int offset = editor.getCaretModel().getOffset();
      if (offset >= document.getTextLength()) offset = document.getTextLength() - 1;
      final PsiElement element = file.findElementAt(offset);
      if (element == null) return;
      if (checkAndRemove(project, element, document)) return;

      if (checkAndRemove(project, PsiTreeUtil.nextLeaf(element), document)) return;
      if (checkAndRemove(project, PsiTreeUtil.prevLeaf(element), document)) return;

      CommonRefactoringUtil.showErrorHint(project, editor, GroovyIntentionsBundle.message("no.unnecessary.semicolons.found"),
                                          GroovyIntentionsBundle.message("remove.unnecessary.semicolons.name"), null);
    }
  }

  private static boolean checkAndRemove(Project project, @Nullable PsiElement element, Document document) {
    if (element != null && element.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
      if (isColonUnnecessary(element, document.getText(), project)) {
        element.delete();
        return true;
      }
    }
    return false;
  }

  private static boolean isColonUnnecessary(PsiElement colon, String text, Project project) {
    final GrStatement prev = getPreviousStatement(colon);
    final GrStatement next = getNextStatement(colon);

    if (prev == null || next == null) return true;

    final int startOffset = prev.getTextRange().getStartOffset();
    final int endOffset = next.getTextRange().getEndOffset();

    final int offset = colon.getTextRange().getStartOffset();
    final String statementWithoutColon = text.substring(startOffset, offset) + text.substring(offset + 1, endOffset);
    final GroovyFile file = GroovyPsiElementFactory.getInstance(project).createGroovyFile(statementWithoutColon, false, null);
    final GrStatement[] statements = file.getStatements();
    if (statements.length != 2) return false;

    return GroovyRefactoringUtil.checkPsiElementsAreEqual(prev, statements[0]) &&
           GroovyRefactoringUtil.checkPsiElementsAreEqual(next, statements[1]);
  }

  @Nullable
  private static GrStatement getPreviousStatement(PsiElement colon) {
    final PsiElement prev = PsiUtil.skipWhitespacesAndComments(colon.getPrevSibling(), false);
    if (prev instanceof GrStatement) return (GrStatement)prev;
    return null;
  }

  @Nullable
  private static GrStatement getNextStatement(PsiElement colon) {
    final PsiElement next = PsiUtil.skipWhitespacesAndComments(colon.getNextSibling(), true);
    if (next instanceof GrStatement) return (GrStatement)next;
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
