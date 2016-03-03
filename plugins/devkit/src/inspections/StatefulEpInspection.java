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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;

import java.util.List;

public class StatefulEpInspection extends DevKitInspectionBase {
  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiField[] fields = psiClass.getFields();
    if (fields.length == 0) return super.checkClass(psiClass, manager, isOnTheFly);

    final boolean isQuickFix = InheritanceUtil.isInheritor(psiClass, LocalQuickFix.class.getCanonicalName());
    if (isQuickFix || ExtensionPointLocator.isRegisteredExtension(psiClass)) {
      final boolean isProjectComponent = InheritanceUtil.isInheritor(psiClass, ProjectComponent.class.getCanonicalName());

      List<ProblemDescriptor> result = ContainerUtil.newArrayList();
      for (final PsiField field : fields) {
        for (Class c : new Class[]{PsiElement.class, PsiReference.class, Project.class}) {
          if (c == Project.class && (field.hasModifierProperty(PsiModifier.FINAL) || isProjectComponent)) continue;
          String message = c == PsiElement.class
                           ? "Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead" +
                             (isQuickFix ? "; also see LocalQuickFixOnPsiElement" : "")
                           : "Don't use " + c.getSimpleName() + " as a field in extension";
          if (InheritanceUtil.isInheritor(field.getType(), c.getCanonicalName())) {
            result.add(manager.createProblemDescriptor(field, message, true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
          }
        }
      }
      return result.toArray(new ProblemDescriptor[result.size()]);
    }
    return super.checkClass(psiClass, manager, isOnTheFly);
  }
}