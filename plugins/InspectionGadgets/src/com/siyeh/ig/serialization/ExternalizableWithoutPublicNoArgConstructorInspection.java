/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.impl.quickfix.AddDefaultConstructorFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ExternalizableWithoutPublicNoArgConstructorInspection extends ExternalizableWithoutPublicNoArgConstructorInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod constructor = (PsiMethod)infos[1];
    if (constructor == null) {
      final PsiClass aClass = (PsiClass)infos[0];
      if (aClass instanceof PsiAnonymousClass) {
        // can't create constructor for anonymous class
        return null;
      }
      return new DelegatingFix(new AddDefaultConstructorFix(aClass, PsiModifier.PUBLIC));
    }
    else {
      return new MakeConstructorPublicFix();
    }
  }

  private static class MakeConstructorPublicFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("make.constructor.public");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiClass aClass = (PsiClass)classNameIdentifier.getParent();
      if (aClass == null) {
        return;
      }
      final PsiMethod constructor = getNoArgConstructor(aClass);
      if (constructor == null) {
        return;
      }
      constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
    }
  }
}