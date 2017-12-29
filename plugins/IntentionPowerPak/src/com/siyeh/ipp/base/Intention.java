/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Intention extends BaseElementAtCaretIntentionAction {

  private final PsiElementPredicate predicate;

  /**
   * @noinspection AbstractMethodCallInConstructor, OverridableMethodCallInConstructor
   */
  protected Intention() {
    predicate = getElementPredicate();
  }

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

  protected static void replaceExpressionWithNegatedExpressionString(@NotNull String newExpression, @NotNull PsiExpression expression, CommentTracker tracker) {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    PsiExpression expressionToReplace = expression;
    final String expString;
    if (BoolUtils.isNegated(expression)) {
      expressionToReplace = BoolUtils.findNegation(expressionToReplace);
      expString = newExpression;
    }
    else {
      PsiElement parent = expressionToReplace.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        expressionToReplace = (PsiExpression)parent;
        parent = parent.getParent();
      }
      expString = "!(" + newExpression + ')';
    }
    final PsiExpression newCall = factory.createExpressionFromText(expString, expression);
    assert expressionToReplace != null;
    final PsiElement insertedElement = tracker.replaceAndRestoreComments(expressionToReplace, newCall);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(insertedElement);
  }

  @Nullable
  PsiElement findMatchingElement(@Nullable PsiElement element, Editor editor) {
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
  public String getText() {
    //noinspection UnresolvedPropertyKey
    return IntentionPowerPackBundle.message(getPrefix() + ".name");
  }

  @NotNull
  public String getFamilyName() {
    //noinspection UnresolvedPropertyKey
    return IntentionPowerPackBundle.defaultableMessage(getPrefix() + ".family.name");
  }
}