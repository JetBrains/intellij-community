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

package com.siyeh.ipp.expression;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementEditorPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.PsiSelectionSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FlipSetterCallIntention extends Intention {
  private static final PsiElementPredicate PREDICATE = new SetterCallPredicate();

  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final Editor editor = getEditorByElementIfItHasSelection(element);
    if (editor == null) {
      if (element instanceof PsiMethodCallExpression) {
        flipCall((PsiMethodCallExpression)element);
      }
    }
    else { // editor not null
      final List<PsiMethodCallExpression> methodCalls =
        PsiSelectionSearcher.searchElementsInSelection(editor, element.getProject(), PsiMethodCallExpression.class, false);
      for (PsiMethodCallExpression call : methodCalls) {
        flipCall(call);
      }
      editor.getSelectionModel().removeSelection();
    }
  }

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return PREDICATE;
  }

  private static void flipCall(PsiMethodCallExpression call) {
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (qualifierExpression == null) return;
    final String qualifier1 = qualifierExpression.getText();
    if (qualifier1 == null || qualifier1.length() == 0) return;
    final PsiExpression[] arguments = call.getArgumentList().getExpressions();
    if (arguments.length != 1) return;
    final PsiExpression argument = arguments[0];
    if (!(argument instanceof PsiMethodCallExpression)) return;
    final PsiMethodCallExpression param = (PsiMethodCallExpression)argument;
    qualifierExpression = param.getMethodExpression().getQualifierExpression();
    if (qualifierExpression == null) return;
    final String qualifier2 = qualifierExpression.getText();
    final PsiMethod setter = call.resolveMethod();
    final PsiMethod getter = param.resolveMethod();

    if (getter == null || setter == null) return;

    final PsiMethod get = PropertyUtil.findPropertyGetter(setter.getContainingClass(), PropertyUtil.getPropertyName(setter), false, true);
    final PsiMethod set = PropertyUtil.findPropertySetter(getter.getContainingClass(), PropertyUtil.getPropertyName(getter), false, true);

    if (get == null || set == null) return;

    StringBuilder text = new StringBuilder();
    text.append(qualifier2).append(".").append(set.getName())
      .append("(")
      .append(qualifier1).append(".").append(get.getName()).append("()")
      .append(")");
    final PsiExpression newExpression =
      JavaPsiFacade.getElementFactory(call.getProject()).createExpressionFromText(text.toString(), call.getContext());
    call.replace(newExpression);
  }

  private static boolean isSetGetMethodCall(PsiMethodCallExpression call) {
    final PsiExpression[] params = call.getArgumentList().getExpressions();
    if (params.length != 1) return false;
    if (!(params[0] instanceof PsiMethodCallExpression)) return false;
    final PsiMethodCallExpression call2 = (PsiMethodCallExpression)params[0];

    //check expressions are simple properties
    final PsiElement methodElement = call.getMethodExpression().resolve();
    final PsiElement param = call2.getMethodExpression().resolve();
    if (!(methodElement instanceof PsiMethod)
        || !(param instanceof PsiMethod)
        || !PropertyUtil.isSimplePropertySetter((PsiMethod)methodElement)
        || !PropertyUtil.isSimplePropertyGetter((PsiMethod)param)) {
      return false;
    }
    final PsiMethod setter1 = (PsiMethod)methodElement;
    final PsiMethod getter2 = (PsiMethod)param;

    //check types compatibility
    if (!call.getArgumentList().getExpressionTypes()[0].equals(getter2.getReturnType())) return false;

    //check both classes have getters/setters
    final PsiMethod getter1 =
      PropertyUtil.findPropertyGetter(setter1.getContainingClass(), PropertyUtil.getPropertyName(setter1), false, true);
    if (getter1 == null) return false;

    final PsiMethod setter2 =
      PropertyUtil.findPropertyGetter(getter2.getContainingClass(), PropertyUtil.getPropertyName(getter2), false, true);
    if (setter2 == null) return false;

    return true;
  }

  @Nullable
  private static Editor getEditorByElementIfItHasSelection(@NotNull PsiElement element) {
    final Editor editor = FileEditorManager.getInstance(element.getProject()).getSelectedTextEditor();
    return editor != null && editor.getSelectionModel().hasSelection() ? editor : null;
  }

  private static class SetterCallPredicate extends PsiElementEditorPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @Nullable Editor editor) {
      boolean underCorrectElement = element instanceof PsiMethodCallExpression && isSetGetMethodCall((PsiMethodCallExpression)element);
      if (editor == null || !editor.getSelectionModel().hasSelection()) {
        return underCorrectElement;
      }

      final List<PsiMethodCallExpression> list =
        PsiSelectionSearcher.searchElementsInSelection(editor, element.getProject(), PsiMethodCallExpression.class, false);
      for (PsiMethodCallExpression methodCallExpression : list) {
        if (isSetGetMethodCall(methodCallExpression)) return true;
      }

      return underCorrectElement;
    }
  }
}
