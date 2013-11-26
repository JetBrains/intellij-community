/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * User: anna
 * Date: 10/24/13
 */
public class SuppressionAnnotationInspection extends SuppressionAnnotationInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore suppressions", myAllowedSuppressions);
    return form.getContentPanel();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof PsiAnnotation) {
      final PsiAnnotation annotation = (PsiAnnotation)infos[0];
      final Collection<String> ids = JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation((PsiModifierList)annotation.getParent());
      if (!ids.isEmpty()) {
        return new InspectionGadgetsFix() {
          @Override
          protected void doFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement psiElement = descriptor.getPsiElement();
            if (psiElement instanceof PsiAnnotation) {
              final Collection<String> ids = JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation((PsiModifierList)psiElement.getParent());
              for (String id : ids) {
                if (!myAllowedSuppressions.contains(id)) {
                  myAllowedSuppressions.add(id);
                }
              }
              saveProfile(project);
            }
          }

          private void saveProfile(Project project) {
            final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
          }

          @NotNull
          @Override
          public String getName() {
            return "Allow these suppressions";
          }

          @NotNull
          @Override
          public String getFamilyName() {
            return "Allow suppressions";
          }
        };
      }
    }
    return null;
  }
}
