/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MethodNameSameAsClassNameInspection extends MethodNameSameAsClassNameInspectionBase {
  private static final Set<String> MODIFIERS_ALLOWED_ON_CONSTRUCTORS = ContainerUtil.set(
    // JLS 8.8.3
    PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE
  );

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final Boolean onTheFly = (Boolean)infos[0];
    final Boolean canBeConvertedToConstructor = (Boolean)infos[1];
    List<InspectionGadgetsFix> fixes = new ArrayList<>();
    if (onTheFly) {
      fixes.add(new RenameFix());
    }
    if (canBeConvertedToConstructor) {
      fixes.add(new MethodNameSameAsClassNameFix());
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  private static class MethodNameSameAsClassNameFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("make.method.ctr.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod method = ObjectUtils.tryCast(element.getParent(), PsiMethod.class);
      if (method == null) return;
      final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement == null) return;
      PsiModifierList modifiers = method.getModifierList();
      for (String modifier : PsiModifier.MODIFIERS) {
        if (!MODIFIERS_ALLOWED_ON_CONSTRUCTORS.contains(modifier)) {
          modifiers.setModifierProperty(modifier, false);
        }
      }
      returnTypeElement.delete();
    }
  }
}