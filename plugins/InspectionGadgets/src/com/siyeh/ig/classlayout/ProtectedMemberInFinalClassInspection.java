/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiModifier.PRIVATE;

public class ProtectedMemberInFinalClassInspection extends BaseInspection {

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[] {
      new RemoveModifierFix((String)infos[0]),
      new MakePrivateFix()
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

  private static class MakePrivateFix extends InspectionGadgetsFix {
    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return currentFile;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("make.private.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
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
      final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
      if (member instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)member;
        SuperMethodsSearch.search(method, method.getContainingClass(), true, false).forEach(
          methodSignature -> {
            final PsiMethod superMethod = methodSignature.getMethod();
              conflicts.putValue(superMethod, InspectionGadgetsBundle.message(
                "0.will.have.incompatible.access.privileges.with.super.1",
                RefactoringUIUtil.getDescription(method, false),
                RefactoringUIUtil.getDescription(superMethod, true)));
            return true;
          });
        OverridingMethodsSearch.search(method).forEach(overridingMethod -> {
          conflicts.putValue(overridingMethod, InspectionGadgetsBundle.message(
            "0.will.no.longer.be.visible.from.overriding.1",
            RefactoringUIUtil.getDescription(method, false),
            RefactoringUIUtil.getDescription(overridingMethod, true)));
          return false;
        });
      }
      final PsiModifierList modifierListCopy = (PsiModifierList)modifierList.copy();
      modifierListCopy.setModifierProperty(PRIVATE, true);
      final Query<PsiReference> search = ReferencesSearch.search(member, member.getResolveScope());
      search.forEach(reference -> {
        final PsiElement element1 = reference.getElement();
        if (!JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, element1, null, null)) {
          final PsiElement context =
            PsiTreeUtil.getParentOfType(element1, PsiMethod.class, PsiField.class, PsiClass.class, PsiFile.class);
          assert context != null;
          conflicts.putValue(element1, RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                                                 RefactoringUIUtil.getDescription(member, false),
                                                                 JavaPsiBundle.visibilityPresentation(PRIVATE),
                                                                 RefactoringUIUtil.getDescription(context, true)));
        }
        return true;
      });
      final boolean conflictsDialogOK;
      if (conflicts.isEmpty()) {
        conflictsDialogOK = true;
      } else {
        if (!isOnTheFly()) {
          return;
        }
        final ConflictsDialog conflictsDialog = new ConflictsDialog(member.getProject(), conflicts,
                                                                    () -> WriteAction.run(() -> modifierList.setModifierProperty(PRIVATE, true)));
        conflictsDialogOK = conflictsDialog.showAndGet();
      }
      if (conflictsDialogOK) {
        WriteAction.run(() -> modifierList.setModifierProperty(PRIVATE, true));
      }
    }
  }

  private static class ProtectedMemberInFinalClassVisitor extends BaseInspectionVisitor {

    private void checkMember(@NotNull PsiMember member) {
      if (!member.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null || 
          !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (member instanceof PsiMethod && MethodUtils.hasSuper((PsiMethod)member)) {
        return;
      }
      registerModifierError(PsiModifier.PROTECTED, member, PsiModifier.PROTECTED);
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