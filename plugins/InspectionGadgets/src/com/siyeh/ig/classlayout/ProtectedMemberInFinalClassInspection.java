/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public class ProtectedMemberInFinalClassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[]{
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
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        performFix(descriptor.getPsiElement());
      });
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      List<PsiElement> elements = Stream.of(descriptors).map(d -> ((ProblemDescriptor)d).getPsiElement()).toList();
      ApplicationEx application = ApplicationManagerEx.getApplicationEx();
      application.runWriteActionWithCancellableProgressInDispatchThread(InspectionGadgetsBundle.message("make.static.quickfix"), project,
                                                                        null, indicator -> {
          WriteCommandAction.writeCommandAction(project, elements)
            .withName(InspectionGadgetsBundle.message("make.static.quickfix"))
            .withGlobalUndo()
            .run(() -> {
              indicator.setIndeterminate(false);
              for (int i = 0; i < elements.size(); i++) {
                PsiElement e = elements.get(i);
                indicator.setFraction((double) i / elements.size());
                performFix(e);
              }
            });
        });
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      performFix(previewDescriptor.getPsiElement());
      return IntentionPreviewInfo.DIFF;
    }

    private static void performFix(PsiElement element) {
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMember)) return;
      final PsiMember member = (PsiMember)grandParent;
      final PsiModifierList modifierList = member.getModifierList();
      if (modifierList == null) return;
      final PsiModifierList modifierListCopy = (PsiModifierList)modifierList.copy();
      modifierListCopy.setModifierProperty(PsiModifier.PRIVATE, true);
      final boolean canBePrivate = ReferencesSearch.search(member, member.getUseScope()).allMatch(
        reference -> JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, reference.getElement(),
                                                  findAccessObjectClass(reference, member), null));
      modifierList.setModifierProperty(canBePrivate ? PsiModifier.PRIVATE : PsiModifier.PACKAGE_LOCAL, true);
    }

    @Nullable
    private static PsiClass findAccessObjectClass(@NotNull PsiReference reference, @NotNull PsiMember member) {
      if (!(reference instanceof PsiJavaCodeReferenceElement)) return null;
      PsiElement qualifier = ((PsiJavaCodeReferenceElement)reference).getQualifier();
      if (!(qualifier instanceof PsiExpression)) return null;
      PsiClass accessObjectClass = null;
      JavaResolveResult accessClass = PsiUtil.getAccessObjectClass((PsiExpression)qualifier);
      PsiElement element = accessClass.getElement();
      if (element instanceof PsiTypeParameter) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(member.getContainingFile().getProject());
        PsiClassType type = factory.createType((PsiTypeParameter)element);
        PsiType accessType = accessClass.getSubstitutor().substitute(type);
        if (accessType instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(member.getContainingFile());
          accessObjectClass = factory.getArrayClass(languageLevel);
        }
        else if (accessType instanceof PsiClassType) {
          accessObjectClass = ((PsiClassType)accessType).resolve();
        }
      }
      else if (element instanceof PsiClass) {
        accessObjectClass = (PsiClass)element;
      }
      return accessObjectClass;
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