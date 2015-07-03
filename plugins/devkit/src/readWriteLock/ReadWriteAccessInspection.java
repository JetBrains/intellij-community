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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.readWriteLock.LockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ReadWriteAccessInspection extends LocalInspectionTool {
  private static final String LOCK_REQUIRED = "com.intellij.util.readWriteLock.LockRequired";
  private static final String LOCK_PROVIDED = "com.intellij.util.readWriteLock.LockProvided";
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
      case 0: return null;
      case 1: return LockType.READ;
      case 2: return LockType.WRITE;
      default: throw new IllegalStateException("level should be from 0 to 2");
    }
  }

  @Nullable
  private static LockType getLockTypeFromDefinition(@Nullable PsiElement definition) {
    if (!(definition instanceof PsiModifierListOwner)) {
      return null;
    }
    return getLockTypeFromAnnotation(
      AnnotationUtil.findAnnotation(((PsiModifierListOwner)definition), LOCK_REQUIRED));
  }




  private enum ProblemType {
    LOCK_REQUEST_LOST,
    LOCK_LEVEL_RAISED
  }

  private static class MyInspectionVisitor extends JavaElementVisitor {

    @NotNull
    ProblemsHolder myHolder;

    public MyInspectionVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitMethod(PsiMethod method) {
      final MyMethodLockComputingVisitor visitor = new MyMethodLockComputingVisitor();
      method.acceptChildren(visitor);

      final LockType bodyType = getLockByLevel(visitor.mySeverity);
      final LockType methodType = getLockTypeFromDefinition(method);

      final ProblemType problem = checkAssignment(bodyType, methodType);
      if (problem == ProblemType.LOCK_REQUEST_LOST) {
        reportProblemIfNeeded(problem, visitor.myPlacesWithLastSeverity);
      } else {
        final PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier != null) {
          reportProblemIfNeeded(problem, identifier);
        }
      }
    }

    @Override
    public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList callerArgs = PsiTreeUtil.getParentOfType(aClass, PsiExpressionList.class);
      if (callerArgs != null) {
        final PsiElement parent = callerArgs.getParent();
        if (parent instanceof PsiCall) {
          final PsiMethod method = ((PsiCall)parent).resolveMethod();
          if (method != null) {
            final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, LOCK_REQUIRED);
          }
        }
      }

      super.visitAnonymousClass(aClass);
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

      final LockType varType = getLockTypeFromDefinition(variable);
      final LockType initType = tryToGetExpressionType(initializer);

      reportProblemIfNeeded(checkAssignment(initType, varType), variable);
    }

    @Nullable
    private static LockType tryToGetExpressionType(@NotNull PsiExpression expr) {
      if (expr instanceof PsiReferenceExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)expr).resolve();
        return getLockTypeFromDefinition(resolved);
      }
      else if (expr instanceof PsiNewExpression) {
        LockType lockType = null;

        final PsiJavaCodeReferenceElement classRef = ((PsiNewExpression)expr).getClassOrAnonymousClassReference();
        if (classRef != null) {
          // TODO analyze class methods
          lockType = getLockTypeFromDefinition(classRef.resolve());
        }
        // TODO wrong logic
        if (lockType != null) {
          return lockType;
        }
        // TODO analyze anonymous
        return null;
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
    public void visitCallExpression(PsiCallExpression callExpression) {
      final CandidateInfo[] resolved =
        PsiResolveHelper.SERVICE.getInstance(callExpression.getProject()).getReferencedMethodCandidates(callExpression, false, false);
      if (resolved.length != 1) {
        return;
      }

      final CandidateInfo info = resolved[0];
      if (!info.isValidResult()) {
        return;
      }

      final LockType methodLockType = getLockTypeFromDefinition(info.getElement());
      final int level = getLockLevel(methodLockType);

      if (level > mySeverity) {
        mySeverity = level;
        myPlacesWithLastSeverity = new ArrayList<PsiElement>(Collections.singleton(callExpression));
      } else if (level == mySeverity && level > 0) {
        myPlacesWithLastSeverity.add(callExpression);
      }

      // TODO check enclosing class

      super.visitCallExpression(callExpression);
    }
  }
}
