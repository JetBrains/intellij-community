// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UtilityClassWithoutPrivateConstructorInspectionBase extends BaseInspection {
  @SuppressWarnings({"PublicField"})
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();
  @SuppressWarnings({"PublicField"})
  public boolean ignoreClassesWithOnlyMain = false;

  @Nullable
  static PsiMethod getNullArgConstructor(PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    for (final PsiMethod constructor : constructors) {
      final PsiParameterList params = constructor.getParameterList();
      if (params.getParametersCount() == 0) {
        return constructor;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("utility.class.without.private.constructor.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("utility.class.without.private.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UtilityClassWithoutPrivateConstructorVisitor();
  }


  private class UtilityClassWithoutPrivateConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (ignoreClassesWithOnlyMain && hasOnlyMain(aClass)) {
        return;
      }
      if (hasPrivateConstructor(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations, 0)) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.PRIVATE) && aClass.getConstructors().length == 0) {
        return;
      }
      final SearchScope scope = GlobalSearchScope.projectScope(aClass.getProject());
      final Query<PsiClass> query = ClassInheritorsSearch.search(aClass, scope, true);
      final PsiClass subclass = query.findFirst();
      if (subclass != null) {
        return;
      }
      registerClassError(aClass, aClass);
    }

    private boolean hasOnlyMain(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length == 0) {
        return false;
      }
      for (PsiMethod method : methods) {
        if (method.isConstructor()) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
          return false;
        }
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
          return false;
        }
        final String name = method.getName();
        if (!name.equals(HardcodedMethodConstants.MAIN)) {
          return false;
        }
        final PsiType returnType = method.getReturnType();
        if (!PsiType.VOID.equals(returnType)) {
          return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
          return false;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter parameter = parameters[0];
        final PsiType type = parameter.getType();
        if (!type.equalsToText("java.lang.String[]")) {
          return false;
        }
      }
      return true;
    }

    boolean hasPrivateConstructor(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return true;
        }
      }
      return false;
    }
  }
}
