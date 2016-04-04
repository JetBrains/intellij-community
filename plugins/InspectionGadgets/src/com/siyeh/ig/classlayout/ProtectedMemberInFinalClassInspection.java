/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiModifier.PRIVATE;

public class ProtectedMemberInFinalClassInspection extends ProtectedMemberInFinalClassInspectionBase {

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[] {
      new RemoveModifierFix((String)infos[0]),
      new MakePrivateFix()
    };
  }

  private static class MakePrivateFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("make.private.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
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
      final MultiMap<PsiElement, String> conflicts = new MultiMap();
      if (member instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)member;
        SuperMethodsSearch.search(method, method.getContainingClass(), true, false).forEach(
        new Processor<MethodSignatureBackedByPsiMethod>() {
          @Override
          public boolean process(MethodSignatureBackedByPsiMethod methodSignature) {
            final PsiMethod superMethod = methodSignature.getMethod();
              conflicts.putValue(superMethod, InspectionGadgetsBundle.message(
                "0.will.have.incompatible.access.privileges.with.super.1",
                RefactoringUIUtil.getDescription(method, false),
                RefactoringUIUtil.getDescription(superMethod, true)));
            return true;
          }
        });
      OverridingMethodsSearch.search(method).forEach(new Processor<PsiMethod>() {
        @Override
        public boolean process(PsiMethod overridingMethod) {
            conflicts.putValue(overridingMethod, InspectionGadgetsBundle.message(
              "0.will.no.longer.be.visible.from.overriding.1",
              RefactoringUIUtil.getDescription(method, false),
              RefactoringUIUtil.getDescription(overridingMethod, true)));
          return false;
        }
      });
      }
      final PsiModifierList modifierListCopy = (PsiModifierList)modifierList.copy();
      modifierListCopy.setModifierProperty(PsiModifier.PRIVATE, true);
      final Query<PsiReference> search = ReferencesSearch.search(member, member.getResolveScope());
      search.forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference reference) {
          final PsiElement element = reference.getElement();
          if (!JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, element, null, null)) {
            final PsiElement context =
              PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiField.class, PsiClass.class, PsiFile.class);
            conflicts.putValue(element, RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                                                  RefactoringUIUtil.getDescription(member, false),
                                                                  PsiBundle.visibilityPresentation(PsiModifier.PRIVATE),
                                                                  RefactoringUIUtil.getDescription(context, true)));
          }
          return true;
        }
      });
      final boolean conflictsDialogOK;
      if (conflicts.isEmpty()) {
        conflictsDialogOK = true;
      } else {
        if (!isOnTheFly()) {
          return;
        }
        final ConflictsDialog conflictsDialog = new ConflictsDialog(member.getProject(), conflicts, new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                modifierList.setModifierProperty(PRIVATE, true);
              }
            });
          }
        });
        conflictsDialogOK = conflictsDialog.showAndGet();
      }
      if (conflictsDialogOK) {
        modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      }
    }
  }
}