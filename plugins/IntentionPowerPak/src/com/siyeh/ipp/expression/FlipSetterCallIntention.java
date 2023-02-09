// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.expression;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
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

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("flip.setter.call.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return IntentionPowerPackBundle.message("flip.setter.call.intention.name");
  }

  @Override
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

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SetterCallPredicate();
  }

  private static void flipCall(PsiMethodCallExpression call) {
    final PsiExpression[] arguments = call.getArgumentList().getExpressions();
    if (arguments.length != 1) return;
    final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
    if (!(argument instanceof PsiMethodCallExpression call2)) return;

    final PsiExpression qualifierExpression1 = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
    final PsiExpression qualifierExpression2 = ExpressionUtils.getEffectiveQualifier(call2.getMethodExpression());
    if (qualifierExpression1 == null || qualifierExpression2 == null) return;
    final PsiMethod setter = call.resolveMethod();
    final PsiMethod getter = call2.resolveMethod();
    final PsiMethod get = PropertyUtil.getReversePropertyMethod(setter);
    final PsiMethod set = PropertyUtil.getReversePropertyMethod(getter);
    if (get == null || set == null) return;
    CommentTracker ct = new CommentTracker();
    final String text =
      ct.text(qualifierExpression2) + "." + set.getName() + "(" + ct.text(qualifierExpression1) + "." + get.getName() + "())";
    ct.replaceAndRestoreComments(call, text);
  }

  private static boolean isSetGetMethodCall(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression call1)) {
      return false;
    }
    final PsiExpression[] arguments = call1.getArgumentList().getExpressions();
    if (arguments.length != 1) {
      return false;
    }
    final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
    if (!(argument instanceof PsiMethodCallExpression call2)) {
      return false;
    }
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
