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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PublicConstructorInNonPublicClassInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return InspectionGadgetsBundle.message("public.constructor.in.non.public.class.problem.descriptor",
      method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorInNonPublicClassVisitor();
  }

  @Override
  public InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList<>();
    final PsiMethod constructor = (PsiMethod)infos[0];
    final PsiClass aClass = constructor.getContainingClass();
    if (aClass != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      fixes.add(new SetConstructorModifierFix(PsiModifier.PRIVATE));
    }
    fixes.add(new RemoveModifierFix(PsiModifier.PUBLIC));
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  private static class SetConstructorModifierFix extends InspectionGadgetsFix {

    @PsiModifier.ModifierConstant private final String modifier;

    SetConstructorModifierFix(@PsiModifier.ModifierConstant String modifier) {
      this.modifier = modifier;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("set.constructor.modifier.fix.family.name");
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "public.constructor.in.non.public.class.quickfix",
        modifier
      );
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiModifierList modifierList = (PsiModifierList)element.getParent();
      modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
      modifierList.setModifierProperty(modifier, true);
    }
  }

  private static class PublicConstructorInNonPublicClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isRecord() && PsiUtil.getLanguageLevel(containingClass) == LanguageLevel.JDK_14_PREVIEW &&
          (JavaPsiRecordUtil.isCompactConstructor(method) || JavaPsiRecordUtil.isExplicitCanonicalConstructor(method))) {
        // compact and canonical constructors in record must be public, according to Java 14-preview spec
        // this restriction is relaxed in Java 15-preview, so the inspection makes sense again
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.PUBLIC) ||
        containingClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (SerializationUtils.isExternalizable(containingClass)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.isEmpty()) {
          return;
        }
      }
      registerModifierError(PsiModifier.PUBLIC, method, method);
    }
  }
}