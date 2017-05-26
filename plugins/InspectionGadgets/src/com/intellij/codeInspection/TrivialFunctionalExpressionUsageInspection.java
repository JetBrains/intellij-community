/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class TrivialFunctionalExpressionUsageInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodReferenceExpression(final PsiMethodReferenceExpression expression) {
        doCheckMethodCallOnFunctionalExpression(expression, element -> expression.resolve() != null);
      }

      @Override
      public void visitLambdaExpression(final PsiLambdaExpression expression) {
        doCheckMethodCallOnFunctionalExpression(expression, ggParent -> {
          final PsiElement callParent = ggParent.getParent();

          final PsiElement body = expression.getBody();
          if (!(body instanceof PsiCodeBlock)) {
            return callParent instanceof PsiStatement || callParent instanceof PsiLocalVariable || expression.isValueCompatible();
          }

          PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
          if (statements.length == 1) {
            return callParent instanceof PsiStatement
                   || callParent instanceof PsiLocalVariable
                   || statements[0] instanceof PsiReturnStatement && expression.isValueCompatible();
          }

          final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements((PsiCodeBlock)body);
          if (returnStatements.length > 1) {
            return false;
          }

          if (returnStatements.length == 1) {
            if (!(ArrayUtil.getLastElement(statements) instanceof PsiReturnStatement)) {
              return false;
            }
            if (returnStatements[0].getReturnValue() != null) {
              if (callParent instanceof PsiLocalVariable) {
                return true;
              }
            }
            else {
              return true;
            }
          }

          return (callParent instanceof PsiStatement && !(callParent instanceof PsiLoopStatement)) ||
                 callParent instanceof PsiLambdaExpression;
        });
      }

      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(aClass, false, Collections.emptySet())) {
          final PsiElement newExpression = aClass.getParent();
          doCheckMethodCallOnFunctionalExpression(ggParent -> {
            final PsiMethod method = aClass.getMethods()[0];
            final PsiCodeBlock body = method.getBody();
            final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(body);
            if (returnStatements.length > 1) {
              return false;
            }
            final PsiElement callParent = ggParent.getParent();
            return callParent instanceof PsiStatement ||
                   callParent instanceof PsiLocalVariable;
          }, newExpression, aClass.getBaseClassType(), new ReplaceAnonymousWithLambdaBodyFix());
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(PsiElement expression,
                                                           Condition<PsiElement> elementContainerCondition) {
        final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiTypeCastExpression) {
          final PsiType interfaceType = ((PsiTypeCastExpression)parent).getType();
          doCheckMethodCallOnFunctionalExpression(elementContainerCondition, parent, interfaceType,
                                                  expression instanceof PsiLambdaExpression ? new ReplaceWithLambdaBodyFix()
                                                                                             : new ReplaceWithMethodReferenceFix());
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(Condition<PsiElement> elementContainerCondition,
                                                           PsiElement parent,
                                                           PsiType interfaceType, 
                                                           LocalQuickFix fix) {
        final PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
        if (gParent instanceof PsiReferenceExpression) {
          final PsiElement ggParent = gParent.getParent();
          if (ggParent instanceof PsiMethodCallExpression) {
            final PsiMethod resolveMethod = ((PsiMethodCallExpression)ggParent).resolveMethod();
            final PsiElement referenceNameElement = ((PsiMethodCallExpression)ggParent).getMethodExpression().getReferenceNameElement();
            if (resolveMethod != null &&
                !resolveMethod.isVarArgs() &&
                ((PsiMethodCallExpression)ggParent).getArgumentList().getExpressions().length == resolveMethod.getParameterList().getParametersCount() &&
                referenceNameElement != null &&
                elementContainerCondition.value(ggParent)) {
              final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
              if (resolveMethod == interfaceMethod || 
                  interfaceMethod != null && MethodSignatureUtil.isSuperMethod(interfaceMethod, resolveMethod)) {
                holder.registerProblem(referenceNameElement, "Method call can be simplified", fix);
              }
            }
          }
        }
      }
    };
  }

  private static void replaceWithLambdaBody(PsiMethodCallExpression callExpression, PsiLambdaExpression element) {
    PsiElement body = element.getBody();
    PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
    if (expression != null) {
      final CommentTracker ct = new CommentTracker();
      inlineCallArguments(callExpression, element, ct);
      ct.replaceAndRestoreComments(callExpression, ct.markUnchanged(expression));
    }
    else if (body instanceof PsiCodeBlock) {
      element = RefactoringUtil.ensureCodeBlock(element);
      if(element == null) return;
      body = element.getBody();
      if(!(body instanceof PsiCodeBlock)) return;
      callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if(callExpression == null) return;
      final CommentTracker ct = new CommentTracker();
      inlineCallArguments(callExpression, element, ct);
      final PsiElement parent = callExpression.getParent();
      final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
      if (statements.length > 0) {
        final PsiStatement anchor = PsiTreeUtil.getParentOfType(parent, PsiStatement.class, false);
        PsiReturnStatement statement = ObjectUtils.tryCast(statements[statements.length - 1], PsiReturnStatement.class);
        if (anchor != null) {
          final PsiElement gParent = anchor.getParent();
          for (PsiElement child : body.getChildren()) {
            if (child != statement && !(child instanceof PsiJavaToken)) {
              gParent.addBefore(ct.markUnchanged(child), anchor);
            }
          }
        }
        final PsiExpression returnValue = statement == null ? null : statement.getReturnValue();
        if (returnValue != null) {
          ct.replaceAndRestoreComments(callExpression, ct.markUnchanged(returnValue));
        } else {
          ct.deleteAndRestoreComments(callExpression);
        }
      }
    }
  }

  private static void inlineCallArguments(PsiMethodCallExpression callExpression,
                                          PsiLambdaExpression element,
                                          CommentTracker ct) {
    final PsiExpression[] args = callExpression.getArgumentList().getExpressions();
    final PsiParameter[] parameters = element.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      final PsiParameter parameter = parameters[i];
      final PsiExpression initializer = args[i];
      for (PsiReference reference : ReferencesSearch.search(parameter)) {
        final PsiElement referenceElement = reference.getElement();
        if (referenceElement instanceof PsiJavaCodeReferenceElement) {
          ct.markUnchanged(initializer);
          InlineUtil.inlineVariable(parameter, initializer, (PsiJavaCodeReferenceElement)referenceElement);
        }
      }
    }
  }

  private static class ReplaceWithLambdaBodyFix extends ReplaceFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace method call on lambda with lambda body";
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      if (qualifierExpression instanceof PsiTypeCastExpression) {
        final PsiExpression element = PsiUtil.skipParenthesizedExprDown(((PsiTypeCastExpression)qualifierExpression).getOperand());
        if (element instanceof PsiLambdaExpression) {
          replaceWithLambdaBody(callExpression, (PsiLambdaExpression)element);
        }
      }
    }
  }

  private static class ReplaceWithMethodReferenceFix extends ReplaceFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace method call on method reference with corresponding method call";
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      if (qualifierExpression instanceof PsiTypeCastExpression) {
        final PsiExpression element = ((PsiTypeCastExpression)qualifierExpression).getOperand();
        if (element instanceof PsiMethodReferenceExpression) {
          final PsiLambdaExpression lambdaExpression =
            LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)element, false, true);
          if (lambdaExpression != null) {
            replaceWithLambdaBody(callExpression, lambdaExpression);
          }
        }
      }
    }
  }

  private static class ReplaceAnonymousWithLambdaBodyFix extends ReplaceFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace call with method body";
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      final PsiExpression cast = AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(qualifierExpression, true, false);
      if (cast instanceof PsiTypeCastExpression) {
        final PsiExpression lambdaExpression = ((PsiTypeCastExpression)cast).getOperand();
        if (lambdaExpression instanceof PsiLambdaExpression) {
          replaceWithLambdaBody(callExpression, (PsiLambdaExpression)lambdaExpression);
        }
      }
    }
  }

  private static abstract class ReplaceFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(psiElement, PsiMethodCallExpression.class);
      if (callExpression != null) {
        fixExpression(callExpression, PsiUtil.skipParenthesizedExprDown(callExpression.getMethodExpression().getQualifierExpression()));
      }
    }

    protected abstract void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression);
  }
}
