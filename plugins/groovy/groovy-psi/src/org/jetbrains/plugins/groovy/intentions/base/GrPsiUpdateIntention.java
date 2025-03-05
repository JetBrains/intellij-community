// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.function.Supplier;

public abstract class GrPsiUpdateIntention implements ModCommandAction {
  private final Supplier<PsiElementPredicate> predicate = new SynchronizedClearableLazy<>(this::getElementPredicate);

  @Override
  public final @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PsiElement matchingElement = findMatchingElement(context);
    if (matchingElement == null) {
      return ModCommand.nop();
    }
    return ModCommand.psiUpdate(matchingElement, (e, updater) -> processIntention(e, context, updater));
  }

  protected abstract void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater);

  @Nullable
  PsiElement findMatchingElement(@NotNull ActionContext context) {
    PsiFile file = context.file();
    if (!file.getViewProvider().getLanguages().contains(GroovyLanguage.INSTANCE)) {
      return null;
    }

    TextRange selection = context.selection();
    if (!selection.isEmpty()) {
      int start = selection.getStartOffset();
      int end = selection.getEndOffset();

      if (0 <= start && start <= end) {
        TextRange selectionRange = new TextRange(start, end);
        PsiElement element = PsiImplUtil.findElementInRange(file, start, end, PsiElement.class);
        while (element != null && element.getTextRange() != null && selectionRange.contains(element.getTextRange())) {
          if (predicate.get().satisfiedBy(element)) return element;
          element = element.getParent();
        }
      }
    }

    PsiElement element = context.findLeaf();
    while (element != null) {
      if (predicate.get().satisfiedBy(element)) return element;
      if (isStopElement(element)) break;
      element = element.getParent();
    }

    element = context.findLeafOnTheLeft();
    while (element != null) {
      if (predicate.get().satisfiedBy(element)) return element;
      if (isStopElement(element)) return null;
      element = element.getParent();
    }

    return null;
  }

  protected boolean isStopElement(PsiElement element) {
    return element instanceof PsiFile;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiElement element = findMatchingElement(context);
    if (element == null) return null;
    return Presentation.of(getText(element));
  }

  protected abstract @NotNull PsiElementPredicate getElementPredicate();

  protected static void replaceExpressionWithNegatedExpressionString(@NotNull String newExpression, @NotNull GrExpression expression) throws
                                                                                                                                      IncorrectOperationException {
    Intention.replaceExpressionWithNegatedExpressionString(newExpression, expression);
  }
  private String getPrefix() {
    final Class<? extends GrPsiUpdateIntention> aClass = getClass();
    final String name = aClass.getSimpleName();
    final StringBuilder buffer = new StringBuilder(name.length() + 10);
    buffer.append(Character.toLowerCase(name.charAt(0)));
    for (int i = 1; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        buffer.append('.');
        buffer.append(Character.toLowerCase(c));
      }
      else {
        buffer.append(c);
      }
    }
    return buffer.toString();
  }

  public @NotNull @IntentionName String getText(@NotNull PsiElement element) {
    return GroovyIntentionsBundle.message(getPrefix() + ".name");
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyIntentionsBundle.message(getPrefix() + ".family.name");
  }
}
