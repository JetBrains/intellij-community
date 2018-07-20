/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class AbstractMethodWithMissingImplementationsInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "abstract.method.with.missing.implementations.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "abstract.method.with.missing.implementations.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbstractMethodWithMissingImplementationsVisitor();
  }

  private static class AbstractMethodWithMissingImplementationsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (method.getNameIdentifier() == null || !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final InheritorFinder inheritorFinder = new InheritorFinder(containingClass);
      for (final PsiClass inheritor : inheritorFinder.getInheritors()) {
        if (hasMatchingImplementation(inheritor, method, containingClass)) {
          continue;
        }
        if (inheritor.isEnum()) {
          final List<PsiEnumConstant> enumConstants = PsiTreeUtil.getChildrenOfTypeAsList(inheritor, PsiEnumConstant.class);
          for (PsiEnumConstant enumConstant : enumConstants) {
            final PsiEnumConstantInitializer initializingClass = enumConstant.getInitializingClass();
            if (initializingClass == null || !hasMatchingImplementation(initializingClass, method, containingClass)) {
              registerMethodError(method);
              return;
            }
          }
          continue;
        }
        registerMethodError(method);
        return;
      }
    }

    private static boolean hasMatchingImplementation(@NotNull PsiClass aClass, @NotNull PsiMethod method, @NotNull PsiClass superClass) {
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || !aClass.isInheritor(superClass, true)) {
        return true;
      }
      final PsiMethod overridingMethod = findOverridingMethod(aClass, method, superClass);
      if (overridingMethod == null ||
          overridingMethod.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      if (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        return true;
      }
      final PsiManager manager = overridingMethod.getManager();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      return facade.arePackagesTheSame(superClass, aClass);
    }

    /**
     * @param method the method of which to find an override.
     * @param superClass
     * @param aClass subclass to find the method in.
     * @return the overriding method.
     */
    @Nullable
    private static PsiMethod findOverridingMethod(PsiClass aClass, @NotNull PsiMethod method, @NotNull PsiClass superClass) {
      final PsiSubstitutor substitutor =
        TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      final MethodSignature signature = method.getSignature(substitutor);
      final List<Pair<PsiMethod, PsiSubstitutor>> pairs =
        aClass.findMethodsAndTheirSubstitutorsByName(signature.getName(), true);
      for (Pair<PsiMethod, PsiSubstitutor> pair : pairs) {
        final PsiMethod overridingMethod = pair.first;
        if (overridingMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          continue;
        }
        final PsiClass containingClass = overridingMethod.getContainingClass();
        if (containingClass != null && containingClass.isInterface() && !overridingMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
          continue;
        }
        final PsiSubstitutor overridingSubstitutor = pair.second;
        final MethodSignature foundMethodSignature =
          overridingMethod.getSignature(overridingSubstitutor);
        if (MethodSignatureUtil.isSubsignature(signature,
                                               foundMethodSignature) && overridingMethod != method) {
          return overridingMethod;
        }
      }
      return null;
    }
  }

  private static class InheritorFinder implements Runnable {

    private final PsiClass aClass;
    private Collection<PsiClass> inheritors = null;

    InheritorFinder(PsiClass aClass) {
      this.aClass = aClass;
    }

    @Override
    public void run() {
      final SearchScope searchScope = aClass.getUseScope();
      inheritors = ClassInheritorsSearch.search(aClass, searchScope, true)
        .findAll();
    }

    public Collection<PsiClass> getInheritors() {
      final ProgressManager progressManager =
        ProgressManager.getInstance();
      // do not display progress
      progressManager.runProcess(this, null);
      return inheritors;
    }
  }
}
