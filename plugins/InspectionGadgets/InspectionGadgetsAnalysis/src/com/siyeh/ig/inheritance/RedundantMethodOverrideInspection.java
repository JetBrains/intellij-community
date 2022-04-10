// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
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
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TrackingEquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RedundantMethodOverrideInspection extends BaseInspection {

  public boolean checkLibraryMethods = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    boolean delegatesToSuperMethod = (boolean)infos[0];
    return delegatesToSuperMethod
           ? InspectionGadgetsBundle.message("redundant.method.override.delegates.to.super.problem.descriptor")
           : InspectionGadgetsBundle.message("redundant.method.override.problem.descriptor");
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("redundant.method.override.option.check.library.methods"),
                                          this, "checkLibraryMethods");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RedundantMethodOverrideFix();
  }

  private static class RedundantMethodOverrideFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.method.override.quickfix");
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

  private class RedundantMethodOverrideVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor()) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null || method.getNameIdentifier() == null) {
        return;
      }
      final PsiMethod[] superMethods = method.findSuperMethods();
      if (superMethods.length == 0) {
        return;
      }
      PsiMethod superMethod = null;
      if (superMethods.length > 1) {
        for (PsiMethod candidate : superMethods) {
          final PsiClass aClass = candidate.getContainingClass();
          if (aClass != null && !aClass.isInterface()) {
            superMethod = candidate;
            break;
          }
        }
      }
      else {
        superMethod = superMethods[0];
      }
      if (superMethod == null ||
          !AbstractMethodOverridesAbstractMethodInspection.methodsHaveSameAnnotationsAndModifiers(method, superMethod) ||
          !AbstractMethodOverridesAbstractMethodInspection.methodsHaveSameReturnTypes(method, superMethod) ||
          !AbstractMethodOverridesAbstractMethodInspection.haveSameExceptionSignatures(method, superMethod) ||
          method.isVarArgs() != superMethod.isVarArgs()) {
        return;
      }
      if (isSuperCallWithSameArguments(body, method, superMethod)) {
        registerMethodError(method, Boolean.TRUE);
        return;
      }
      if (checkLibraryMethods && superMethod instanceof PsiCompiledElement) {
        final PsiElement navigationElement = superMethod.getNavigationElement();
        if (!(navigationElement instanceof PsiMethod)) {
          return;
        }
        superMethod = (PsiMethod)navigationElement;
      }
      final PsiCodeBlock superBody = superMethod.getBody();
      final PsiMethod finalSuperMethod = superMethod;
      final TrackingEquivalenceChecker checker = new TrackingEquivalenceChecker() {
        @Override
        protected boolean equivalentDeclarations(PsiElement element1, PsiElement element2) {
          if (super.equivalentDeclarations(element1, element2)) {
            return true;
          }
          return checkLibraryMethods && element1.getNavigationElement().equals(element2.getNavigationElement());
        }

        @Override
        protected PsiClass getQualifierTarget(PsiReferenceExpression ref) {
          PsiClass target = super.getQualifierTarget(ref);
          if (target == method.getContainingClass() && target == ClassUtils.getContainingClass(ref)) {
            return finalSuperMethod.getContainingClass();
          }
          return target;
        }

        @Override
        protected @NotNull Match thisExpressionsMatch(@NotNull PsiThisExpression thisExpression1,
                                                      @NotNull PsiThisExpression thisExpression2) {
          final PsiClass containingClass1 = PsiUtil.resolveClassInClassTypeOnly(thisExpression1.getType());
          final PsiClass containingClass2 = PsiUtil.resolveClassInClassTypeOnly(thisExpression2.getType());
          if (containingClass1 == finalSuperMethod.getContainingClass()) {
            if (containingClass2 == method.getContainingClass()) {
              return EXACT_MATCH;
            }
          }
          else if (containingClass1 == containingClass2) {
            return EXACT_MATCH;
          }
          return EXACT_MISMATCH;
        }
      };
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        checker.markDeclarationsAsEquivalent(parameters[i], superParameters[i]);
      }
      checker.markDeclarationsAsEquivalent(method, superMethod);
      if (checker.codeBlocksAreEquivalent(body, superBody)) {
        registerMethodError(method, Boolean.FALSE);
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
          expression = PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue());
        }
        else {
          return false;
        }
      }
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      if (!MethodCallUtils.isSuperMethodCall(methodCallExpression, method)) {
        return false;
      }
      final PsiMethod targetMethod = methodCallExpression.resolveMethod();
      if (targetMethod != superMethod) {
        return false;
      }

      if (superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
        final PsiJavaFile file = (PsiJavaFile)method.getContainingFile();
        // implementing a protected method in another package makes it available to that package.
        PsiPackage aPackage = JavaPsiFacade.getInstance(method.getProject()).findPackage(file.getPackageName());
        if (aPackage == null) {
          return false; // when package statement is incorrect
        }
        final PackageScope scope = new PackageScope(aPackage, false, false);
        if (isOnTheFly()) {
          final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(method.getProject());
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

    private boolean areSameArguments(PsiMethodCallExpression methodCallExpression, PsiMethod method) {
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