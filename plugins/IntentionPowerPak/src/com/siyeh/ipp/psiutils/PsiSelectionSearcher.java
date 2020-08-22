// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.siyeh.ipp.psiutils;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class PsiSelectionSearcher {
  private PsiSelectionSearcher() {
  }

  /**
   * Searches elements in selection
   *
   * @param editor          editor to get text selection
   * @param project         Project
   * @param filter          PsiElement filter, e.g. PsiMethodCallExpression.class
   * @param searchChildrenOfFound if true, visitor will look for matching elements in the children of a found element, otherwise will not look inside found element.
   * @param <T>             type based on PsiElement type
   * @return elements in selection
   */
  @NotNull
  public static <T extends PsiElement> List<T> searchElementsInSelection(Editor editor,
                                                                         Project project,
                                                                         final Class<T> filter,
                                                                         final boolean searchChildrenOfFound) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return Collections.emptyList();
    }
    final TextRange selection = new UnfairTextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null || file instanceof PsiCompiledElement) {
      return Collections.emptyList();
    }
    final List<T> results = new ArrayList<>();

    final PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!selection.intersects(element.getTextRange())) {
          return;
        }
        if (filter.isAssignableFrom(element.getClass())) {
          results.add((T)element);
          if (!searchChildrenOfFound) {
            return;
          }
        }
        super.visitElement(element);
      }
    };

    file.accept(visitor);
    return results;
  }
}
