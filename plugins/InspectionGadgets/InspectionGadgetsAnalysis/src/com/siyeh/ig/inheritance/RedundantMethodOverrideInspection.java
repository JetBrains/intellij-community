/*
 * Copyright 2005-2017 Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantMethodOverrideInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "redundant.method.override.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "redundant.method.override.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RedundantMethodOverrideFix();
  }

  private static class RedundantMethodOverrideFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "redundant.method.override.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiElement method = methodNameIdentifier.getParent();
      assert method != null;
      deleteElement(method);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantMethodOverrideVisitor();
  }

  private static class RedundantMethodOverrideVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      if (superMethod == null) {
        return;
      }
      if (!MethodUtils.haveEquivalentModifierLists(method, superMethod)) {
        return;
      }
      final PsiType superReturnType = superMethod.getReturnType();
      if (superReturnType == null || !superReturnType.equals(method.getReturnType())) {
        return;
      }
      final PsiCodeBlock superBody = superMethod.getBody();

      final EquivalenceChecker checker = new ParameterEquivalenceChecker(method, superMethod);
      if (checker.codeBlocksAreEquivalent(body, superBody) || isSuperCallWithSameArguments(body, method, superMethod)) {
        registerMethodError(method);
      }
    }

    private static class ParameterEquivalenceChecker extends EquivalenceChecker {
      private final PsiMethod myMethod;
      private final PsiMethod mySuperMethod;

      ParameterEquivalenceChecker(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
        myMethod = method;
        mySuperMethod = superMethod;
      }

      @Override
      protected Match referenceExpressionsMatch(PsiReferenceExpression referenceExpression1,
                                                                PsiReferenceExpression referenceExpression2) {
        if (areSameParameters(referenceExpression1, referenceExpression2)) {
          return EXACT_MATCH;
        }
        return super.referenceExpressionsMatch(referenceExpression1, referenceExpression2);
      }

      private boolean areSameParameters(PsiReferenceExpression referenceExpression1, PsiReferenceExpression referenceExpression2) {
        // parameters of super method and overridden method should be considered the same
        PsiElement resolved1 = referenceExpression1.resolve();
        PsiElement resolved2 = referenceExpression2.resolve();
        if (!(resolved1 instanceof PsiParameter) || !(resolved2 instanceof PsiParameter)) return false;
        PsiElement scope1 = ((PsiParameter)resolved1).getDeclarationScope();
        PsiElement scope2 = ((PsiParameter)resolved2).getDeclarationScope();
        if (scope1 == scope2 || scope1 != myMethod && scope1 != mySuperMethod || scope2 != myMethod && scope2 != mySuperMethod) {
          return false;
        }
        PsiElement parent1 = resolved1.getParent();
        PsiElement parent2 = resolved2.getParent();
        if (!(parent1 instanceof PsiParameterList) || !(parent2 instanceof PsiParameterList)) {
          return false;
        }
        int index1 = ((PsiParameterList)parent1).getParameterIndex((PsiParameter)resolved1);
        int index2 = ((PsiParameterList)parent2).getParameterIndex((PsiParameter)resolved2);

        return index1 == index2;
      }
    }

    private boolean isSuperCallWithSameArguments(PsiCodeBlock body, PsiMethod method, PsiMethod superMethod) {
      final PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return false;
      }
      final PsiStatement statement = statements[0];
      final PsiExpression expression;
      if (PsiType.VOID.equals(method.getReturnType())) {
        if (statement instanceof PsiExpressionStatement) {
          final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
          expression = expressionStatement.getExpression();
        }
        else {
          return false;
        }
      }
      else {
        if (statement instanceof PsiReturnStatement) {
          final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
          expression = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
        }
        else {
          return false;
        }
      }
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      if (!MethodCallUtils.isSuperMethodCall(methodCallExpression, method)) return false;

      if (superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
        final PsiJavaFile file = (PsiJavaFile)method.getContainingFile();
        // implementing a protected method in another package makes it available to that package.
        PsiPackage aPackage = JavaPsiFacade.getInstance(method.getProject()).findPackage(file.getPackageName());
        if (aPackage == null) {
          return false; // when package statement is incorrect
        }
        final PackageScope scope = new PackageScope(aPackage, false, false);
        if (isOnTheFly()) {
          final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(method.getProject());
          final PsiSearchHelper.SearchCostResult cost =
            searchHelper.isCheapEnoughToSearch(method.getName(), scope, null, null);
          if (cost == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
            return true;
          }
          if (cost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
            return false;
          }
        }
        final Query<PsiReference> search = ReferencesSearch.search(method, scope);
        final PsiClass containingClass = method.getContainingClass();
        for (PsiReference reference : search) {
          if (!PsiTreeUtil.isAncestor(containingClass, reference.getElement(), true)) {
            return false;
          }
        }
      }

      return areSameArguments(methodCallExpression, method);
    }

    private static boolean areSameArguments(PsiMethodCallExpression methodCallExpression, PsiMethod method) {
      // void foo(int param) { super.foo(42); } is not redundant
      PsiExpression[] arguments = methodCallExpression.getArgumentList().getExpressions();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (arguments.length != parameters.length) return false;
      for (int i = 0; i < arguments.length; i++) {
        PsiExpression argument = arguments[i];
        PsiExpression exp = PsiUtil.deparenthesizeExpression(argument);
        if (!(exp instanceof PsiReferenceExpression)) return false;
        PsiElement resolved = ((PsiReferenceExpression)exp).resolve();
        if (!method.getManager().areElementsEquivalent(parameters[i], resolved)) {
          return false;
        }
      }
      return true;
    }
  }
}