/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ProtectedMemberInFinalClassInspection extends BaseInspection {

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[] {
      new WeakenVisibilityFix()
    };
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("protected.member.in.final.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ProtectedMemberInFinalClassVisitor();
  }

  private static class WeakenVisibilityFix extends InspectionGadgetsFix implements BatchQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("weaken.visibility.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      applyFix(project, new CommonProblemDescriptor[] {descriptor}, List.of(), null);
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        for (CommonProblemDescriptor descriptor : descriptors) {
          final PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
          final PsiElement parent = element.getParent();
          final PsiElement grandParent = parent.getParent();
          if (!(grandParent instanceof PsiMember)) {
            return;
          }
          final PsiMember member = (PsiMember)grandParent;
          final PsiModifierList modifierList = member.getModifierList();
          if (modifierList == null) {
            return;
          }
          final PsiModifierList modifierListCopy = (PsiModifierList)modifierList.copy();
          modifierListCopy.setModifierProperty(PsiModifier.PRIVATE, true);
          final Query<PsiReference> search = ReferencesSearch.search(member, member.getResolveScope());
          final boolean canBePrivate = search.forEach(reference -> {
            return JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, reference.getElement(), null, null);
          });
          modifierList.setModifierProperty(canBePrivate ? PsiModifier.PRIVATE : PsiModifier.PACKAGE_LOCAL, true);
        }
      }, InspectionGadgetsBundle.message("weaken.visibility.quickfix"), true, project);
    }
  }

  private static class ProtectedMemberInFinalClassVisitor extends BaseInspectionVisitor {

    private void checkMember(@NotNull PsiMember member) {
      if (!member.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (member instanceof PsiMethod && !((PsiMethod)member).isConstructor() &&
          !PsiSuperMethodImplUtil.getHierarchicalMethodSignature((PsiMethod)member).getSuperSignatures().isEmpty()) {
        return;
      }
      registerModifierError(PsiModifier.PROTECTED, member);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      checkMember(method);
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      checkMember(field);
    }
  }
}