// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix;
import org.jetbrains.idea.devkit.util.PsiUtil;

/**
 * @author Konstantin Bulenkov
 */
public class InspectionDescriptionNotFoundInspection extends DevKitInspectionBase {
  @NonNls private static final String INSPECTION_PROFILE_ENTRY = DescriptionType.INSPECTION.getClassName();

  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final Project project = psiClass.getProject();
    final PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);

    if (nameIdentifier == null || module == null || !PsiUtil.isInstantiable(psiClass)) return null;

    final PsiClass base = JavaPsiFacade.getInstance(project).findClass(INSPECTION_PROFILE_ENTRY, psiClass.getResolveScope());
    if (base == null || !psiClass.isInheritor(base, true) || isPathMethodsAreOverridden(psiClass)) return null;

    final InspectionDescriptionInfo info = InspectionDescriptionInfo.create(module, psiClass);
    if (!info.isValid() || info.hasDescriptionFile()) return null;

    final PsiElement problemElement = getProblemElement(psiClass, info.getShortNameMethod());
    final ProblemDescriptor problemDescriptor = manager
      .createProblemDescriptor(problemElement == null ? nameIdentifier : problemElement,
                               "Inspection does not have a description", isOnTheFly,
                               new LocalQuickFix[]{new CreateHtmlDescriptionFix(info.getFilename(), module, DescriptionType.INSPECTION)},
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    return new ProblemDescriptor[]{problemDescriptor};
  }

  @Nullable
  private static PsiElement getProblemElement(PsiClass psiClass, @Nullable PsiMethod method) {
    if (method != null && method.getContainingClass() == psiClass) {
      return PsiUtil.getReturnedExpression(method);
    }
    return psiClass.getNameIdentifier();
  }

  private static boolean isPathMethodsAreOverridden(PsiClass psiClass) {
    return !(isLastMethodDefinitionIn("getStaticDescription", INSPECTION_PROFILE_ENTRY, psiClass)
             && isLastMethodDefinitionIn("getDescriptionUrl", INSPECTION_PROFILE_ENTRY, psiClass)
             && isLastMethodDefinitionIn("getDescriptionContextClass", INSPECTION_PROFILE_ENTRY, psiClass)
             && isLastMethodDefinitionIn("getDescriptionFileName", INSPECTION_PROFILE_ENTRY, psiClass));
  }

  private static boolean isLastMethodDefinitionIn(@NotNull String methodName,
                                                  @NotNull String classFQN,
                                                  @Nullable PsiClass psiClass) {
    if (psiClass == null) return false;
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.getName().equals(methodName)) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return false;
        return classFQN.equals(containingClass.getQualifiedName());
      }
    }
    return isLastMethodDefinitionIn(methodName, classFQN, psiClass.getSuperClass());
  }

  @Override
  @NotNull
  public String getShortName() {
    return "InspectionDescriptionNotFoundInspection";
  }

}
