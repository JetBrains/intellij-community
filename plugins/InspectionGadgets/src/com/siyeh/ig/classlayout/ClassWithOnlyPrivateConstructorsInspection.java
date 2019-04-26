// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeClassFinalFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ClassWithOnlyPrivateConstructorsInspection extends BaseInspection {

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MakeClassFinalFix((PsiClass)infos[0]);
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("class.with.only.private.constructors.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.with.only.private.constructors.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithOnlyPrivateConstructorsVisitor();
  }

  private static class ClassWithOnlyPrivateConstructorsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isEnum() || aClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        return;
      }
      for (PsiMethod constructor : constructors) {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
      }
      final PsiClass inheritor = DirectClassInheritorsSearch.search(aClass, new LocalSearchScope(aClass.getContainingFile())).findFirst();
      if (inheritor != null) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}
