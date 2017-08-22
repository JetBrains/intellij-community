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
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TrivialFunctionalExpressionUsageInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodReferenceExpression(final PsiMethodReferenceExpression expression) {
        doCheckMethodCallOnFunctionalExpression(expression, call -> expression.resolve() != null);
      }

      @Override
      public void visitLambdaExpression(final PsiLambdaExpression expression) {
        final PsiElement body = expression.getBody();
        if (body == null) return;

        Predicate<PsiMethodCallExpression> checkBody = call -> {
          final PsiElement callParent = call.getParent();

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
          }

          if(callParent instanceof PsiExpressionStatement) {
            PsiElement statementParent = callParent.getParent();
            // Disable in "for" initialization or update
            if(statementParent instanceof PsiForStatement && callParent != ((PsiForStatement)statementParent).getBody()) {
              return false;
            }
          }

          return (callParent instanceof PsiStatement && !(callParent instanceof PsiLoopStatement)) ||
                 callParent instanceof PsiLambdaExpression;
        };
        Predicate<PsiMethodCallExpression> checkWrites = call ->
          Arrays.stream(expression.getParameterList().getParameters())
            .noneMatch(parameter -> VariableAccessUtils.variableIsAssigned(parameter, body));

        doCheckMethodCallOnFunctionalExpression(expression, checkBody.and(checkWrites));
      }

      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(aClass, false, Collections.emptySet())) {
          final PsiNewExpression newExpression = ObjectUtils.tryCast(aClass.getParent(), PsiNewExpression.class);
          doCheckMethodCallOnFunctionalExpression(call -> {
            final PsiMethod method = aClass.getMethods()[0];
            final PsiCodeBlock body = method.getBody();
            final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(body);
            if (returnStatements.length > 1) {
              return false;
            }
            final PsiElement callParent = call.getParent();
            return callParent instanceof PsiStatement ||
                   callParent instanceof PsiLocalVariable;
          }, newExpression, aClass.getBaseClassType(), new ReplaceAnonymousWithLambdaBodyFix());
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(PsiElement expression,
                                                           Predicate<PsiMethodCallExpression> elementContainerPredicate) {
        final PsiTypeCastExpression parent =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiTypeCastExpression.class);
        if (parent != null) {
          final PsiType interfaceType = parent.getType();
          doCheckMethodCallOnFunctionalExpression(elementContainerPredicate, parent, interfaceType,
                                                  expression instanceof PsiLambdaExpression ? new ReplaceWithLambdaBodyFix()
                                                                                             : new ReplaceWithMethodReferenceFix());
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(Predicate<PsiMethodCallExpression> elementContainerPredicate,
                                                           PsiExpression qualifier,
                                                           PsiType interfaceType,
                                                           LocalQuickFix fix) {
        final PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(qualifier);
        if (call == null) return;

        final PsiMethod method = call.resolveMethod();
        final PsiElement referenceNameElement = call.getMethodExpression().getReferenceNameElement();
        boolean suitableMethod = method != null &&
                                 referenceNameElement != null &&
                                 !method.isVarArgs() &&
                                 call.getArgumentList().getExpressions().length == method.getParameterList().getParametersCount() &&
                                 elementContainerPredicate.test(call);
        if (!suitableMethod) return;
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
        if (method == interfaceMethod || interfaceMethod != null && MethodSignatureUtil.isSuperMethod(interfaceMethod, method)) {
          holder.registerProblem(referenceNameElement, "Method call can be simplified", fix);
        }
      }
    };
  }

  private static void replaceWithLambdaBody(PsiLambdaExpression lambda) {
    lambda = extractSideEffects(lambda);
    if (lambda == null) return;
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return;
    PsiElement body = lambda.getBody();
    PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
    if (expression != null) {
      replaceExpression(callExpression, lambda);
    }
    else if (body instanceof PsiCodeBlock) {
      replaceCodeBlock(lambda);
    }
  }

  private static PsiLambdaExpression extractSideEffects(PsiLambdaExpression lambda) {
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return lambda;
    PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (Stream.of(arguments).noneMatch(SideEffectChecker::mayHaveSideEffects)) return lambda;

    lambda = RefactoringUtil.ensureCodeBlock(lambda);
    if (lambda == null) return null;
    callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return lambda;
    arguments = callExpression.getArgumentList().getExpressions();
    PsiParameter[] parameters = lambda.getParameterList().getParameters();
    if (arguments.length != parameters.length) return lambda;
    PsiStatement anchor = PsiTreeUtil.getParentOfType(lambda, PsiStatement.class, false);
    if (anchor == null) return lambda;

    List<PsiStatement> statements = new ArrayList<>();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(lambda.getProject());

    for (int i = 0; i < arguments.length; i++) {
      PsiExpression argument = arguments[i];
      PsiParameter parameter = parameters[i];
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(argument);
      if (!sideEffects.isEmpty()) {
        boolean used = VariableAccessUtils.variableIsUsed(parameter, lambda.getBody());
        if (used) {
          String name = parameter.getName();
          if (name == null) continue;
          statements.add(
            factory.createStatementFromText(parameter.getType().getCanonicalText() + " " + name + "=" + argument.getText()+";", lambda));
          argument.replace(factory.createExpressionFromText(name, parameter));
        }
        else {
          Collections.addAll(statements, StatementExtractor.generateStatements(sideEffects, argument));
        }
      }
    }
    BlockUtils.addBefore(anchor, statements.toArray(PsiStatement.EMPTY_ARRAY));
    return lambda;
  }

  private static void replaceExpression(PsiMethodCallExpression callExpression, PsiLambdaExpression element) {
    PsiExpression expression;
    final CommentTracker ct = new CommentTracker();
    inlineCallArguments(callExpression, element, ct);
    // body could be invalidated after inlining
    expression = LambdaUtil.extractSingleExpressionFromBody(element.getBody());
    ct.replaceAndRestoreComments(callExpression, ct.markUnchanged(expression));
  }

  private static void replaceCodeBlock(PsiLambdaExpression element) {
    element = RefactoringUtil.ensureCodeBlock(element);
    if (element == null) return;
    PsiElement body = element.getBody();
    if (!(body instanceof PsiCodeBlock)) return;
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (callExpression == null) return;
    final CommentTracker ct = new CommentTracker();
    inlineCallArguments(callExpression, element, ct);
    body = element.getBody();
    final PsiElement parent = callExpression.getParent();
    final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
    PsiReturnStatement statement = null;
    if (statements.length > 0) {
      final PsiStatement anchor = PsiTreeUtil.getParentOfType(parent, PsiStatement.class, false);
      statement = ObjectUtils.tryCast(statements[statements.length - 1], PsiReturnStatement.class);
      if (anchor != null) {
        final PsiElement gParent = anchor.getParent();
        for (PsiElement child : body.getChildren()) {
          if (child != statement && !(child instanceof PsiJavaToken)) {
            gParent.addBefore(ct.markUnchanged(child), anchor);
          }
        }
      }
    }
    final PsiExpression returnValue = statement == null ? null : statement.getReturnValue();
    if (returnValue != null) {
      ct.replaceAndRestoreComments(callExpression, ct.markUnchanged(returnValue));
    }
    else {
      ct.deleteAndRestoreComments(callExpression);
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
          replaceWithLambdaBody((PsiLambdaExpression)element);
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
            replaceWithLambdaBody(lambdaExpression);
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
          replaceWithLambdaBody((PsiLambdaExpression)lambdaExpression);
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
