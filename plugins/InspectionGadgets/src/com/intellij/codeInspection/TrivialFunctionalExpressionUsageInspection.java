/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TrivialFunctionalExpressionUsageInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodReferenceExpression(final PsiMethodReferenceExpression expression) {
        doCheckMethodCallOnFunctionalExpression(expression, new Condition<PsiElement>() {
          @Override
          public boolean value(PsiElement element) {
            return expression.resolve() != null;
          }
        });
      }

      @Override
      public void visitLambdaExpression(final PsiLambdaExpression expression) {
        doCheckMethodCallOnFunctionalExpression(expression, new Condition<PsiElement>() {
          @Override
          public boolean value(PsiElement ggParent) {
            final PsiElement body = expression.getBody();
            if (!(body instanceof PsiCodeBlock) || 
                ((PsiCodeBlock)body).getStatements().length == 1) {
              return true;
            }
            final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(expression);
            if (returnExpressions.size() > 1) {
              return false;
            }
            final PsiElement callParent = ggParent.getParent();
            if (returnExpressions.isEmpty()) {
              return callParent instanceof PsiStatement;
            }
            return callParent instanceof PsiStatement || 
                   callParent instanceof PsiLocalVariable ||
                   callParent instanceof PsiExpression && PsiTreeUtil.getParentOfType(callParent, PsiStatement.class) != null;
          }
        });
      }

      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(aClass, false)) {
          final PsiElement newExpression = aClass.getParent();
          doCheckMethodCallOnFunctionalExpression(new Condition<PsiElement>() {
            @Override
            public boolean value(PsiElement ggParent) {
              final PsiMethod method = aClass.getMethods()[0];
              final PsiCodeBlock body = method.getBody();
              final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(body);
              if (returnStatements.length > 1) {
                return false;
              }
              final PsiElement callParent = ggParent.getParent();
              return callParent instanceof PsiStatement ||
                     callParent instanceof PsiLocalVariable;
            }
          }, newExpression, aClass.getBaseClassType(), "Replace call with method body");
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(PsiElement expression,
                                                           Condition<PsiElement> elementContainerCondition) {
        final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiTypeCastExpression) {
          final PsiType interfaceType = ((PsiTypeCastExpression)parent).getType();
          doCheckMethodCallOnFunctionalExpression(elementContainerCondition, parent, interfaceType,
                                                  "Replace method call " + 
                                                  (expression instanceof PsiLambdaExpression ? "on lambda with lambda body"
                                                                                             : "on method reference with corresponding method call"));
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(Condition<PsiElement> elementContainerCondition,
                                                           PsiElement parent,
                                                           PsiType interfaceType, 
                                                           String quickFixName) {
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
                final ReplaceWithLambdaBodyFix fix = 
                  new ReplaceWithLambdaBodyFix(quickFixName);
                holder.registerProblem(referenceNameElement, "Method call can be simplified", fix);
              }
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithLambdaBodyFix implements LocalQuickFix {
    private String myName;

    public ReplaceWithLambdaBodyFix(String name) {
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(psiElement)) return;
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(psiElement, PsiMethodCallExpression.class);
      if (callExpression != null) {
        final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(callExpression.getMethodExpression().getQualifierExpression());
        if (qualifierExpression instanceof PsiTypeCastExpression) {
          final PsiExpression element = ((PsiTypeCastExpression)qualifierExpression).getOperand();
          if (element instanceof PsiLambdaExpression) {
            replaceWithLambdaBody(callExpression, (PsiLambdaExpression)element);
          }
          else if (element instanceof PsiMethodReferenceExpression) {
            final PsiLambdaExpression lambdaExpression =
              LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)element, false, true);
            replaceWithLambdaBody(callExpression, lambdaExpression);
          }
        }
        else if (qualifierExpression instanceof PsiNewExpression) {
          final PsiExpression cast = AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(qualifierExpression, true, false);
          if (cast instanceof PsiTypeCastExpression) {
            final PsiExpression lambdaExpression = ((PsiTypeCastExpression)cast).getOperand();
            if (lambdaExpression instanceof PsiLambdaExpression) {
              replaceWithLambdaBody(callExpression, (PsiLambdaExpression)lambdaExpression);
            }
          }
        }
      }
    }

    private static void replaceWithLambdaBody(PsiMethodCallExpression callExpression, PsiLambdaExpression element) {
      inlineCallArguments(callExpression, element);

      final PsiElement body = element.getBody();
      if (body instanceof PsiExpression) {
        callExpression.replace(body);
      }
      else if (body instanceof PsiCodeBlock) {
        final PsiElement parent = callExpression.getParent();
        if (parent instanceof PsiStatement) {
          final PsiElement gParent = parent.getParent();
          restoreComments(gParent, parent, body);
          for (PsiStatement statement : ((PsiCodeBlock)body).getStatements()) {
            PsiElement toInsert;
            if (statement instanceof PsiReturnStatement) {
              toInsert = ((PsiReturnStatement)statement).getReturnValue();
            }
            else {
              toInsert = statement;
            }

            if (toInsert != null) {
              gParent.addBefore(toInsert, parent);
            }
          }
          parent.delete();
        }
        else {
          final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
          if (statements.length > 0) {
            final PsiStatement anchor = PsiTreeUtil.getParentOfType(parent, PsiStatement.class);
            if (anchor != null) {
              final PsiElement gParent = anchor.getParent();
              restoreComments(gParent, anchor, body);
              for (int i = 0; i < statements.length - 1; i++) {
                gParent.addBefore(statements[i], anchor);
              }
            }
            final PsiExpression returnValue = ((PsiReturnStatement)statements[statements.length - 1]).getReturnValue();
            if (returnValue != null) {
              callExpression.replace(returnValue);
            }
          }
        }
      }
    }

    private static void restoreComments(PsiElement gParent, PsiElement parent, PsiElement body) {
      for (PsiElement comment : PsiTreeUtil.findChildrenOfType(body, PsiComment.class)) {
        gParent.addBefore(comment, parent);
      }
    }

    private static void inlineCallArguments(PsiMethodCallExpression callExpression, PsiLambdaExpression element) {
      final PsiExpression[] args = callExpression.getArgumentList().getExpressions();
      final PsiParameter[] parameters = element.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        final PsiExpression initializer = args[i];
        for (PsiReference reference : ReferencesSearch.search(parameter)) {
          final PsiElement referenceElement = reference.getElement();
          if (referenceElement instanceof PsiJavaCodeReferenceElement) {
            InlineUtil.inlineVariable(parameter, initializer, (PsiJavaCodeReferenceElement)referenceElement);
          }
        }
      }
    }
  }
}
