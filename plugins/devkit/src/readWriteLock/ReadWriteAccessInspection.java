/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.readWriteLock;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.readWriteLock.LockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ReadWriteAccessInspection extends LocalInspectionTool {
  private static final String LOCK_REQUIRED = "com.intellij.util.readWriteLock.LockRequired";
  private static final String LOCK_PROVIDED = "com.intellij.util.readWriteLock.LockProvided";
  private static final String LOCK_ANONYMOUS = "com.intellij.util.readWriteLock.LockAnonymous";
  private static final String ATTR_NAME = "value";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new MyInspectionVisitor(holder);
  }

  @Nullable
  private static LockType getLockTypeFromAnnotation(@Nullable PsiAnnotation lock) {
    if (lock == null) {
      return null;
    }
    final PsiAnnotationMemberValue level = lock.findAttributeValue(ATTR_NAME);
    if (level == null || !(level instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiElement levelDef = ((PsiReferenceExpression)level).resolve();
    if (levelDef == null || !(levelDef instanceof PsiEnumConstant)) {
      return null;
    }

    final String name = ((PsiEnumConstant)levelDef).getName();
    if (name == null) {
      return null;
    }
    return LockType.valueOf(name);
  }

  private static int getLockLevel(@Nullable LockType type) {
    if (type == null) {
      return 0;
    }
    if (type == LockType.READ) {
      return 1;
    }
    if (type == LockType.WRITE) {
      return 2;
    }
    throw new IllegalStateException("all enum constants were checked");
  }

  @Nullable
  private static LockType getLockByLevel(int level) {
    switch (level) {
      case 0:
        return null;
      case 1:
        return LockType.READ;
      case 2:
        return LockType.WRITE;
      default:
        throw new IllegalStateException("level should be from 0 to 2");
    }
  }

  @Nullable
  private static LockType getLockTypeFromDefinition(@Nullable PsiElement definition, @NotNull String annotationName) {
    if (!(definition instanceof PsiModifierListOwner)) {
      return null;
    }
    return getLockTypeFromAnnotation(
      AnnotationUtil.findAnnotation(((PsiModifierListOwner)definition), annotationName));
  }

  // For now it's just simple expression casting; should be replaced by some data flow analisys
  @Nullable
  private static LockType tryToGetExpressionType(@NotNull PsiExpression expr) {
    if (expr instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)expr).resolve();
      return getLockTypeFromDefinition(resolved, LOCK_ANONYMOUS);
    }
    else if (expr instanceof PsiNewExpression) {
      // We want to analyze only anonymous ones since if it's just instantiation,
      // we know everything about all methods of the class so annotation is useless
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)expr).getAnonymousClass();
      if (anonymousClass == null) {
        return null;
      }

      final PsiJavaCodeReferenceElement classRef = ((PsiNewExpression)expr).getClassOrAnonymousClassReference();
      // We don't want to work with anonymous ones if they don't resolve
      if (classRef == null) {
        return null;
      }

      final LockType baseLock = getLockTypeFromDefinition(classRef.resolve(), LOCK_REQUIRED);
      final LockType newLock = getLockFromClassMethods(anonymousClass, false);

      if (getLockLevel(newLock) > getLockLevel(baseLock)) {
        return newLock;
      }
      else {
        return null;
      }
    }
    else if (expr instanceof PsiMethodCallExpression) {
      final PsiElement element = resolveCallStrictly((PsiMethodCallExpression)expr);
      if (!(element instanceof PsiMethod)) {
        return null;
      }
      return getLockTypeFromDefinition(element, LOCK_ANONYMOUS);
    }
    else if (expr instanceof PsiLambdaExpression) {
      // TODO analyze anonymous
      return null;
    }
    return null;
  }

  @Nullable
  private static ProblemType checkAssignment(@Nullable LockType typeFrom, @Nullable LockType typeTo) {
    final int levelFrom = getLockLevel(typeFrom);
    final int levelTo = getLockLevel(typeTo);
    if (levelFrom == levelTo) {
      return null;
    }
    else if (levelFrom > levelTo) {
      return ProblemType.LOCK_REQUEST_LOST;
    }
    else {
      return ProblemType.LOCK_LEVEL_RAISED;
    }
  }

  @Nullable
  private static LockType getLockFromClassMethods(@NotNull PsiClass clazz, boolean seeSuper) {
    final PsiMethod[] methods;
    if (seeSuper) {
      methods = clazz.getAllMethods();
    }
    else {
      methods = clazz.getMethods();
    }

    int maxSeverity = 0;
    for (PsiMethod method : methods) {
      maxSeverity = Math.max(maxSeverity, getLockLevel(getLockTypeFromDefinition(method, LOCK_REQUIRED)));
    }
    return getLockByLevel(maxSeverity);
  }

  @Nullable
  private static PsiElement resolveCallStrictly(@NotNull PsiCallExpression callExpression) {
    final CandidateInfo[] resolved =
      PsiResolveHelper.SERVICE.getInstance(callExpression.getProject()).getReferencedMethodCandidates(callExpression, false, false);
    if (resolved.length != 1) {
      return null;
    }

    final CandidateInfo info = resolved[0];
    if (!info.isValidResult()) {
      return null;
    }
    return info.getElement();
  }


  private enum ProblemType {
    LOCK_REQUEST_LOST,
    LOCK_LEVEL_RAISED,
    BOTH_PROVIDED_AND_REQUIRE_USED
  }

  private static class MyInspectionVisitor extends JavaElementVisitor {

    @NotNull
    ProblemsHolder myHolder;

    public MyInspectionVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitMethod(PsiMethod method) {
      analyzeMethodLockRequirement(method);
      analyzeMethodLockReturn(method);
    }

    private void analyzeMethodLockReturn(@NotNull PsiMethod method) {
      final LockType methodLock = getLockTypeFromDefinition(method, LOCK_ANONYMOUS);

      final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(method);
      for (PsiReturnStatement statement : returnStatements) {
        final PsiExpression expr = statement.getReturnValue();
        if (expr == null) {
          return;
        }

        final LockType returnLock = tryToGetExpressionType(expr);
        if (returnLock != methodLock) {
          reportProblemIfNeeded(checkAssignment(returnLock, methodLock), statement);
        }
      }
    }

    private void analyzeMethodLockRequirement(@NotNull PsiMethod method) {
      final MyMethodLockComputingVisitor visitor = new MyMethodLockComputingVisitor();
      method.acceptChildren(visitor);

      final LockType bodyType = getLockByLevel(visitor.mySeverity);
      final LockType methodType = getLockTypeFromDefinition(method, LOCK_REQUIRED);
      final LockType methodSuppress = getLockTypeFromDefinition(method, LOCK_PROVIDED);

      final ProblemType problem;
      if (methodType != null && methodSuppress != null) {
        problem = ProblemType.BOTH_PROVIDED_AND_REQUIRE_USED;
      }
      else {
        final LockType methodLock = methodType != null ? methodType : methodSuppress;
        problem = checkAssignment(bodyType, methodLock);
      }

      if (problem == ProblemType.LOCK_REQUEST_LOST) {
        reportProblemIfNeeded(problem, visitor.myPlacesWithLastSeverity);
      }
      else {
        final PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier != null) {
          reportProblemIfNeeded(problem, identifier);
        }
      }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      final PsiExpression r = expression.getRExpression();
      if (r == null) {
        return;
      }

      final LockType lType = tryToGetExpressionType(expression.getLExpression());
      final LockType rType = tryToGetExpressionType(r);

      reportProblemIfNeeded(checkAssignment(rType, lType), expression);
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return;
      }

      final LockType varType = getLockTypeFromDefinition(variable, LOCK_ANONYMOUS);
      final LockType initType = tryToGetExpressionType(initializer);

      reportProblemIfNeeded(checkAssignment(initType, varType), variable);
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      final PsiElement element = resolveCallStrictly(callExpression);
      if (!(element instanceof PsiMethod)) {
        return;
      }

      final PsiMethod method = (PsiMethod)element;
      final PsiParameterList parameters = method.getParameterList();
      final PsiExpressionList arguments = callExpression.getArgumentList();

      if (arguments == null || parameters.getParametersCount() != arguments.getExpressions().length) {
        return;
      }

      for (int paramN = 0; paramN < parameters.getParametersCount(); paramN++) {
        final PsiParameter parameter = parameters.getParameters()[paramN];
        final PsiExpression argument = arguments.getExpressions()[paramN];

        final LockType parameterType = getLockTypeFromDefinition(parameter, LOCK_ANONYMOUS);
        final LockType argumentType = tryToGetExpressionType(argument);

        reportProblemIfNeeded(checkAssignment(argumentType, parameterType), argument);
      }
    }

    private void reportProblemIfNeeded(@Nullable ProblemType problem, @NotNull PsiElement place) {
      reportProblemIfNeeded(problem, Collections.singleton(place));
    }

    private void reportProblemIfNeeded(@Nullable ProblemType problem, @NotNull Collection<PsiElement> places) {
      if (problem == null) {
        return;
      }

      for (PsiElement element : places) {
        myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(
          element, problem.name(), LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true, false));
      }
    }
  }

  private static class MyMethodLockComputingVisitor extends JavaRecursiveElementVisitor {
    private int mySeverity = 0;
    private Collection<PsiElement> myPlacesWithLastSeverity = new ArrayList<PsiElement>();

    @Override
    public void visitClass(PsiClass aClass) {
      // stop here
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
      final LockType methodLockType = getLockTypeFromCall(callExpression);
      final int level = getLockLevel(methodLockType);

      if (level > mySeverity) {
        mySeverity = level;
        myPlacesWithLastSeverity = new ArrayList<PsiElement>(Collections.singleton(callExpression));
      }
      else if (level == mySeverity && level > 0) {
        myPlacesWithLastSeverity.add(callExpression);
      }

      // TODO check enclosing class

      super.visitCallExpression(callExpression);
    }

    @Nullable
    private static LockType getLockTypeFromCall(@NotNull PsiCallExpression callExpression) {
      final PsiElement resolvedMethod = resolveCallStrictly(callExpression);

      final LockType methodLockType = getLockTypeFromDefinition(resolvedMethod, LOCK_REQUIRED);
      LockType anonymousType = null;

      if (callExpression instanceof PsiMethodCallExpression) {
        final PsiReferenceExpression expression = ((PsiMethodCallExpression)callExpression).getMethodExpression();
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
          anonymousType = tryToGetExpressionType(qualifier);
        }
      }

      return getLockLevel(methodLockType) > getLockLevel(anonymousType) ? methodLockType : anonymousType;
    }
  }
}
