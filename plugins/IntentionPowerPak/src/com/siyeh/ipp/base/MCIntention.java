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

import com.intellij.codeInspection.EditorUpdater;
import com.intellij.codeInspection.ModCommands;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A (mostly) drop-in ModCommandAction replacement for {@link Intention}
 */
public abstract class MCIntention implements ModCommandAction {
  private final Supplier<PsiElementPredicate> myPredicate = new SynchronizedClearableLazy<>(this::getElementPredicate);

  @Override
  public final @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PsiElement matchingElement = findMatchingElement(context);
    if (matchingElement == null) {
      return ModCommands.nop();
    }
    return ModCommands.psiUpdate(matchingElement, (e, updater) -> processIntention(context, updater, e));
  }

  protected void processIntention(@NotNull PsiElement element) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void processIntention(@NotNull ActionContext context, @NotNull EditorUpdater updater, @NotNull PsiElement element) {
    processIntention(element);
  }

  @NotNull
  protected abstract PsiElementPredicate getElementPredicate();


  @Nullable
  PsiElement findMatchingElement(@Nullable PsiElement element) {
    if (element == null || !JavaLanguage.INSTANCE.equals(element.getLanguage())) return null;

    PsiElementPredicate predicate = myPredicate.get();
    if (predicate instanceof PsiElementEditorPredicate) {
      throw new UnsupportedOperationException("Editor predicate is not supported");
    }

    while (element != null) {
      if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
        break;
      }
      if (predicate.satisfiedBy(element)) {
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
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiElement element = findMatchingElement(context);
    if (element == null) return null;
    String text = getTextForElement(element);
    return Presentation.of(text == null ? getFamilyName() : text);
  }

  @Nullable
  protected PsiElement findMatchingElement(@NotNull ActionContext context) {
    PsiElement leaf = context.findLeaf();
    PsiElement element = findMatchingElement(leaf);
    if (element != null) return element;
    PsiElement leftLeaf = context.findLeafOnTheLeft();
    if (leftLeaf != leaf) return findMatchingElement(leftLeaf);
    return null;
  }

  @IntentionName
  protected abstract @Nullable String getTextForElement(@NotNull PsiElement element);
}