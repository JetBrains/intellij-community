/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.siyeh.ipp.expression;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
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

  protected void processIntention(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      final List<PsiMethodCallExpression> methodCalls =
        PsiSelectionSearcher.searchElementsInSelection(editor, project, PsiMethodCallExpression.class, false);
      if (!methodCalls.isEmpty()) {
        for (PsiMethodCallExpression call : methodCalls) {
          flipCall(call);
        }
        editor.getSelectionModel().removeSelection();
        return;
      }
    }
    if (element instanceof PsiMethodCallExpression) {
      flipCall((PsiMethodCallExpression)element);
    }
  }

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SetterCallPredicate();
  }

  private static void flipCall(PsiMethodCallExpression call) {
    final PsiExpression qualifierExpression1 = call.getMethodExpression().getQualifierExpression();
    if (qualifierExpression1 == null) {
      return;
    }
    final PsiExpression[] arguments = call.getArgumentList().getExpressions();
    if (arguments.length != 1) {
      return;
    }
    final PsiExpression argument = arguments[0];
    if (!(argument instanceof PsiMethodCallExpression)) {
      return;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)argument;
    final PsiExpression qualifierExpression2 = methodCallExpression.getMethodExpression().getQualifierExpression();
    if (qualifierExpression2 == null) {
      return;
    }
    final PsiMethod setter = call.resolveMethod();
    final PsiMethod getter = methodCallExpression.resolveMethod();
    final PsiMethod get = PropertyUtil.getReversePropertyMethod(setter);
    final PsiMethod set = PropertyUtil.getReversePropertyMethod(getter);
    if (get == null || set == null) {
      return;
    }
    final String text =
      qualifierExpression2.getText() + "." + set.getName() + "(" + qualifierExpression1.getText() + "." + get.getName() + "())";
    final PsiExpression newExpression = JavaPsiFacade.getElementFactory(call.getProject()).createExpressionFromText(text, call);
    call.replace(newExpression);
  }

  private static boolean isSetGetMethodCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call1 = (PsiMethodCallExpression)element;
    final PsiExpression[] arguments = call1.getArgumentList().getExpressions();
    if (arguments.length != 1) {
      return false;
    }
    final PsiExpression argument = arguments[0];
    if (!(argument instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call2 = (PsiMethodCallExpression)argument;
    final PsiMethod setter = call1.resolveMethod();
    final PsiMethod getter = call2.resolveMethod();
    final PsiMethod get = PropertyUtil.getReversePropertyMethod(setter);
    final PsiMethod set = PropertyUtil.getReversePropertyMethod(getter);
    if (setter == null || getter == null || get == null || set == null) {
      return false;
    }

    //check types compatibility
    final PsiParameter[] parameters = setter.getParameterList().getParameters();
    if (parameters.length != 1) {
      return false;
    }
    final PsiParameter parameter = parameters[0];
    return parameter.getType().equals(getter.getReturnType());
  }

  private static class SetterCallPredicate extends PsiElementEditorPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @Nullable Editor editor) {
      if (editor != null && editor.getSelectionModel().hasSelection()) {
        final List<PsiMethodCallExpression> list =
          PsiSelectionSearcher.searchElementsInSelection(editor, element.getProject(), PsiMethodCallExpression.class, false);
        for (PsiMethodCallExpression methodCallExpression : list) {
          if (isSetGetMethodCall(methodCallExpression)) {
            return true;
          }
        }
      }
      return isSetGetMethodCall(element);
    }
  }
}
