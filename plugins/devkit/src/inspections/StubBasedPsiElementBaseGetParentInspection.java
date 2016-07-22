/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class StubBasedPsiElementBaseGetParentInspection extends DevKitInspectionBase {

  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (psiClass.isInterface() ||
        psiClass.isEnum() ||
        !psiClass.hasModifierProperty(PsiModifier.PUBLIC) ||
        psiClass.hasModifierProperty(PsiModifier.STATIC) ||
        psiClass.hasModifierProperty(PsiModifier.ABSTRACT) ||
        psiClass.getContainingClass() != null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    if (!InheritanceUtil.isInheritor(psiClass, true, StubBasedPsiElementBase.class.getName())) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    PsiMethod myParentTemplate = createGetParentTemplateMethod(psiClass);

    final PsiMethod overriddenGetParentMethod = psiClass.findMethodBySignature(myParentTemplate, true);
    if (overriddenGetParentMethod != null &&
        overriddenGetParentMethod.getContainingClass() != null &&
        !StubBasedPsiElementBase.class.getName().equals(overriddenGetParentMethod.getContainingClass().getQualifiedName())) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    final ProblemDescriptor descriptor =
      manager.createProblemDescriptor(ObjectUtils.assertNotNull(psiClass.getNameIdentifier()),
                                      "Default getParent() implementation is slow",
                                      new InsertGetParentByStubOverrideQuickFix(psiClass, isOnTheFly),
                                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
    return new ProblemDescriptor[]{descriptor};
  }

  private static LightMethodBuilder createGetParentTemplateMethod(@NotNull PsiClass psiClass) {
    return new LightMethodBuilder(psiClass.getManager(), "getParent")
      .setMethodReturnType(PsiElement.class.getName());
  }

  private static class InsertGetParentByStubOverrideQuickFix extends LocalQuickFixOnPsiElement {

    private final boolean myOnTheFly;

    private InsertGetParentByStubOverrideQuickFix(@NotNull PsiClass psiClass, boolean isOnTheFly) {
      super(psiClass);
      myOnTheFly = isOnTheFly;
    }

    @NotNull
    @Override
    public String getText() {
      return "Override with calling getParentByStub()";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      if (!ReadonlyStatusHandler.ensureFilesWritable(project, file.getVirtualFile())) return;

      final PsiClass psiClass = (PsiClass)startElement;

      final PsiClass stubBasedPsiElementClass =
        JavaPsiFacade.getInstance(project).findClass(StubBasedPsiElementBase.class.getName(), psiClass.getResolveScope());
      assert stubBasedPsiElementClass != null;

      final PsiMethod methodToOverride =
        stubBasedPsiElementClass.findMethodBySignature(createGetParentTemplateMethod(stubBasedPsiElementClass), false);
      final List<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethod(psiClass, methodToOverride, false);
      if (methods.size() != 1) {
        return;
      }

      PsiMethod getParentMethod = ContainerUtil.getFirstItem(methods);
      assert getParentMethod != null;
      final PsiCodeBlock originalCodeBlock = getParentMethod.getBody();
      assert originalCodeBlock != null;

      final PsiCodeBlock optimizedCodeBlock =
        JavaPsiFacade.getElementFactory(project).createCodeBlockFromText("{ return getParentByStub(); }", getParentMethod);
      originalCodeBlock.replace(optimizedCodeBlock);

      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(stubBasedPsiElementClass, psiClass, PsiSubstitutor.EMPTY);
      final PsiElement anchor = OverrideImplementUtil.getDefaultAnchorToOverrideOrImplement(psiClass, methodToOverride, substitutor);
      final List<PsiGenerationInfo<PsiMethod>> generationInfos =
        GenerateMembersUtil.insertMembersBeforeAnchor(psiClass, anchor,
                                                      Collections.singletonList(new PsiGenerationInfo<>(getParentMethod)));

      if (myOnTheFly) {
        final PsiGenerationInfo<PsiMethod> item = ContainerUtil.getFirstItem(generationInfos);
        if (item != null) PsiNavigateUtil.navigate(item.getPsiMember());
      }
    }
  }
}
