// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.devkit.DevKitBundle;

@VisibleForTesting
@ApiStatus.Internal
public final class InspectionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {
  @NonNls private static final String INSPECTION_PROFILE_ENTRY = InspectionProfileEntry.class.getName();

  public InspectionDescriptionNotFoundInspection() {
    super(DescriptionType.INSPECTION);
  }

  @Override
  protected boolean skipIfNotRegistered(PsiClass epClass) {
    return isAnyPathMethodOverridden(epClass);
  }

  @Override
  protected boolean checkDynamicDescription(ProblemsHolder holder,
                                            Module module,
                                            PsiClass psiClass) {
    final InspectionDescriptionInfo info = InspectionDescriptionInfo.create(module, psiClass);
    return info.isValid() && info.hasDescriptionFile();
  }

  @Override
  protected @Nullable String getDescriptionDir(Module module, PsiClass psiClass) {
    return InspectionDescriptionInfo.create(module, psiClass).getFilename();
  }

  @Override
  protected @InspectionMessage @NotNull String getHasNotDescriptionError(Module module,
                                                                         PsiClass psiClass) {
    final InspectionDescriptionInfo info = InspectionDescriptionInfo.create(module, psiClass);
    final PsiMethod shortNameMethod = info.getShortNameMethod();
    final String methodName = shortNameMethod == null ? "" : " [" + shortNameMethod.getName() + "()]";
    return DevKitBundle.message("inspections.inspection.description.optional.short.name", methodName);
  }

  @Override
  protected @InspectionMessage @NotNull String getHasNotBeforeAfterError() {
    return "";
  }

  private static boolean isAnyPathMethodOverridden(PsiClass psiClass) {
    return !(isLastMethodDefinitionIn("getStaticDescription", psiClass)
             && isLastMethodDefinitionIn("getDescriptionContextClass", psiClass)
             && isLastMethodDefinitionIn("getDescriptionFileName", psiClass));
  }

  private static boolean isLastMethodDefinitionIn(@NotNull String methodName,
                                                  @Nullable PsiClass psiClass) {
    if (psiClass == null) return false;
    for (PsiMethod method : psiClass.findMethodsByName(methodName, false)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return false;
      return INSPECTION_PROFILE_ENTRY.equals(containingClass.getQualifiedName());
    }
    return isLastMethodDefinitionIn(methodName, psiClass.getSuperClass());
  }
}
