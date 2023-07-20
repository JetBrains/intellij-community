/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.base;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class Intention extends BaseElementAtCaretIntentionAction {
  @SafeFieldForPreview
  private final Supplier<PsiElementPredicate> myPredicate = new SynchronizedClearableLazy<>(() -> getElementPredicate());

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element){
    final PsiElement matchingElement = findMatchingElement(element, editor);
    if (matchingElement == null) {
      return;
    }
    processIntention(editor, matchingElement);
  }

  protected abstract void processIntention(@NotNull PsiElement element);

  protected void processIntention(Editor editor, @NotNull PsiElement element) {
    processIntention(element);
  }

  @NotNull
  protected abstract PsiElementPredicate getElementPredicate();


  @Nullable
  PsiElement findMatchingElement(@Nullable PsiElement element, Editor editor) {
    if (element == null || !JavaLanguage.INSTANCE.equals(element.getLanguage())) return null;

    PsiElementPredicate predicate = myPredicate.get();

    while (element != null) {
      if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
        break;
      }
      if (predicate instanceof PsiElementEditorPredicate) {
        if (((PsiElementEditorPredicate)predicate).satisfiedBy(element, editor)) {
          return element;
        }
      }
      else if (predicate.satisfiedBy(element)) {
        return element;
      }
      element = element.getParent();
      if (element instanceof PsiFile) {
        break;
      }
    }
    return null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return findMatchingElement(element, editor) != null;
  }
}