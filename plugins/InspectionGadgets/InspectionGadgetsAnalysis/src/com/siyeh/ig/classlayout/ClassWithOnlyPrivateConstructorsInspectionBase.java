/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class ClassWithOnlyPrivateConstructorsInspectionBase extends BaseInspection {
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
      Collection<PsiClass> innerAndLocalClasses = PsiTreeUtil.findChildrenOfType(aClass, PsiClass.class);
      for (PsiClass innerClass : innerAndLocalClasses) {
        if (innerClass.isInheritor(aClass, false)) {
          return;
        }
      }
      registerClassError(aClass, aClass);
    }
  }
}
