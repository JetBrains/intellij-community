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

package com.siyeh.ipp.psiutils;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class PsiSelectionSearcher {
  private PsiSelectionSearcher() {
  }

  /**
   * Searches elements in selection
   *
   * @param editor          editor to get text selection
   * @param project         Project
   * @param filter          PsiElement filter, e.g. PsiMethodCallExpression.class
   * @param dontStopOnFound if true, visitor will look inside found elements. if false, visitor will stop looking for elements in children of found element
   * @param <T>             type based on PsiElement type
   * @return elements in selection
   */
  @NotNull
  public static <T extends PsiElement> List<T> searchElementsInSelection(Editor editor,
                                                                         Project project,
                                                                         final Class<T> filter,
                                                                         final boolean dontStopOnFound) {
    final TextRange selection = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null || file instanceof PsiCompiledElement) return Collections.emptyList();

    final List<T> results = new ArrayList<T>();

    final PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!selection.intersects(element.getTextRange())) return;

        if (filter.isAssignableFrom(element.getClass())) {
          results.add((T)element);
          if (dontStopOnFound) {
            super.visitElement(element);
          }
        }
        else {
          super.visitElement(element);
        }
      }
    };

    file.accept(visitor);
    return results;
  }
}
