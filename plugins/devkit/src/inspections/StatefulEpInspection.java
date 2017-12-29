/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocator;
import org.jetbrains.uast.UClass;

import java.util.List;

public class StatefulEpInspection extends DevKitUastInspectionBase {
  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@NotNull UClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiField[] fields = psiClass.getFields();
    if (fields.length == 0) return super.checkClass(psiClass, manager, isOnTheFly);

    final boolean isQuickFix = InheritanceUtil.isInheritor(psiClass, LocalQuickFix.class.getCanonicalName());
    ExtensionLocator locator = ExtensionLocator.byPsiClass(psiClass);
    List<ExtensionCandidate> targets = locator.findCandidates();
    if (isQuickFix || !targets.isEmpty()) {
      boolean isProjectComponent = InheritanceUtil.isInheritor(psiClass, ProjectComponent.class.getCanonicalName());
      boolean projectService = ContainerUtil.find(targets, candidate -> {
        XmlTag element = candidate.pointer.getElement();
        String name = element != null ? element.getName() : null;
        return "projectService".equals(name);
      }) != null;

      List<ProblemDescriptor> result = ContainerUtil.newArrayList();
      for (PsiField field : fields) {
        for (Class c : new Class[]{PsiElement.class, PsiReference.class, Project.class}) {
          if (c == Project.class && (field.hasModifierProperty(PsiModifier.FINAL) || isProjectComponent || projectService)) continue;
          String message = c == PsiElement.class
                           ? "Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead" +
                             (isQuickFix ? "; also see LocalQuickFixOnPsiElement" : "")
                           : "Don't use " + c.getSimpleName() + " as a field in " + ( isQuickFix ? "quick fix" : "extension");
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