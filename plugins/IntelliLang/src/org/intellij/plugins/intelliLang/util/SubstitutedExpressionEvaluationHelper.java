/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import org.intellij.plugins.intelliLang.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Computes the constant value of an expression while considering the substitution annotation for non-compile-time
 * constant expressions.
 * <p/>
 * This is a quite simplified implementation at the moment.
 */
public class SubstitutedExpressionEvaluationHelper {
  private final PsiConstantEvaluationHelper myHelper;
  private final Configuration myConfiguration;

  public SubstitutedExpressionEvaluationHelper(final Project project) {
    myHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
    myConfiguration = Configuration.getInstance();
  }

  @Nullable
  public Object computeSimpleExpression(PsiExpression e) {
    if (e instanceof PsiLiteralExpression) {
      return myHelper.computeConstantExpression(e);
    }
    else if (e instanceof PsiReferenceExpression) {
      final PsiReferenceExpression ref = (PsiReferenceExpression)e;
      final PsiElement psiElement = ref.resolve();
      if (psiElement != null) {
        final Object o = myHelper.computeConstantExpression(e);
        if (o != null) {
          return o;
        }
        else if (psiElement instanceof PsiModifierListOwner) {
          // find substitution
          return calcSubstituted((PsiModifierListOwner)psiElement);
        }
      }
      else {
        // unresolvable... no luck
      }
    }
    else if (e instanceof PsiParenthesizedExpression) {
      return computeSimpleExpression(((PsiParenthesizedExpression)e).getExpression());
    }
    else if (e instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression c = (PsiMethodCallExpression)e;
      final PsiMethod m = (PsiMethod)c.getMethodExpression().resolve();
      if (m != null && m.getReturnType() != PsiType.VOID) {
        // find substitution
        return calcSubstituted(m);
      }
    }
    return myHelper.computeConstantExpression(e);
  }

  private Object calcSubstituted(final PsiModifierListOwner owner) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, myConfiguration.getSubstAnnotationPair().second);
    if (annotation != null) {
      return AnnotationUtilEx.calcAnnotationValue(annotation, "value");
    }
    return null;
  }

  /**
   * Computes the value for the passed expression.
   *
   * @param e           The expression whose value to compute
   * @param nonConstant list that returns non-constant and non-substituted expressions
   * @return the computed value, or null if the expression isn't compile time constant and not susbtituted
   */
  @Nullable
  public static String computeExpression(@NotNull final PsiExpression e, @Nullable List<PsiExpression> nonConstant) {
    final StringBuilder builder = new StringBuilder();
    final List<PsiExpression> list = nonConstant != null ? nonConstant : new SmartList<PsiExpression>();
    final PsiElementVisitor processor = new JavaRecursiveElementVisitor() {
      SubstitutedExpressionEvaluationHelper helper = new SubstitutedExpressionEvaluationHelper(e.getProject());
      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        PsiExpression c = expression.getCondition();
        final Object o = helper.myHelper.computeConstantExpression(c);
        if (Boolean.TRUE.equals(o)) {
          final PsiExpression then = expression.getThenExpression();
          if (then != null) {
            execute(then);
          }
        }
        else if (Boolean.FALSE.equals(o)) {
          final PsiExpression elseExpr = expression.getElseExpression();
          if (elseExpr != null) {
            execute(elseExpr);
          }
        }
        else if (o == null) {
          list.add(expression);
        }
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        execute(expression);
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        execute(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        execute(expression);
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        final PsiExpression expr = expression.getRExpression();
        if (expr != null) expr.accept(this);
      }

      public void execute(PsiExpression e) {
        final Object s = helper.computeSimpleExpression(e);
        if (s != null) {
          builder.append(String.valueOf(s));
        }
        else {
          list.add(e);
        }
      }
    };
    e.accept(processor);

    return list.isEmpty() ? builder.toString() : null;
  }
}
