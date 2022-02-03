// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.BoolUtils;


public abstract class Intention implements IntentionAction {
  @SafeFieldForPreview
  private final PsiElementPredicate predicate;

  /**
   * @noinspection AbstractMethodCallInConstructor
   */
  protected Intention() {
    super();
    predicate = getElementPredicate();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = findMatchingElement(file, editor);
    if (element == null) {
      return;
    }
    assert element.isValid() : element;
    processIntention(element, project, editor);
  }

  protected abstract void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException;

  @NotNull
  protected abstract PsiElementPredicate getElementPredicate();


  protected static void replaceExpressionWithNegatedExpressionString(@NotNull String newExpression, @NotNull GrExpression expression) throws IncorrectOperationException {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());

    GrExpression expressionToReplace = expression;
    final String expString;
    if (BoolUtils.isNegated(expression)) {
      expressionToReplace = BoolUtils.findNegation(expression);
      expString = newExpression;
    }
    else {
      expString = "!(" + newExpression + ')';
    }
    final GrExpression newCall = factory.createExpressionFromText(expString, expression.getContext());
    assert expressionToReplace != null;
    expressionToReplace.replaceWithExpression(newCall, true);
  }


  @Nullable
  PsiElement findMatchingElement(PsiFile file, Editor editor) {
    if (!file.getViewProvider().getLanguages().contains(GroovyLanguage.INSTANCE)) {
      return null;
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();

      if (0 <= start && start <= end) {
        TextRange selectionRange = new TextRange(start, end);
        PsiElement element = PsiImplUtil.findElementInRange(file, start, end, PsiElement.class);
        while (element != null && element.getTextRange() != null && selectionRange.contains(element.getTextRange())) {
          if (predicate.satisfiedBy(element)) return element;
          element = element.getParent();
        }
      }
    }

    final int position = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(position);
    while (element != null) {
      if (predicate.satisfiedBy(element)) return element;
      if (isStopElement(element)) break;
      element = element.getParent();
    }

    element = file.findElementAt(position - 1);
    while (element != null) {
      if (predicate.satisfiedBy(element)) return element;
      if (isStopElement(element)) return null;
      element = element.getParent();
    }

    return null;
  }

  protected boolean isStopElement(PsiElement element) {
    return element instanceof PsiFile;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return findMatchingElement(file, editor) != null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private String getPrefix() {
    final Class<? extends Intention> aClass = getClass();
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

  @Override
  @NotNull
  public @IntentionName String getText() {
    return GroovyIntentionsBundle.message(getPrefix() + ".name");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return GroovyIntentionsBundle.message(getPrefix() + ".family.name");
  }
}
