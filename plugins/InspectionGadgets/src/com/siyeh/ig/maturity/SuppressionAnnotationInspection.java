/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.ui.JBUI;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: anna
 * Date: 10/24/13
 */
public class SuppressionAnnotationInspection extends SuppressionAnnotationInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore suppressions", myAllowedSuppressions);
    final JComponent panel = form.getContentPanel();
    panel.setPreferredSize(JBUI.size(150, 100));
    return panel;
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final boolean suppressionIdPresent = ((Boolean)infos[1]).booleanValue();
    if (infos[0] instanceof PsiAnnotation) {
      final PsiAnnotation annotation = (PsiAnnotation)infos[0];
      return suppressionIdPresent
             ? new InspectionGadgetsFix[]{new DelegatingFix(new RemoveAnnotationQuickFix(annotation, null)), new AllowSuppressionsFix()}
             : new InspectionGadgetsFix[]{new DelegatingFix(new RemoveAnnotationQuickFix(annotation, null))};
    } else if (infos[0] instanceof PsiComment) {
      return suppressionIdPresent
             ? new InspectionGadgetsFix[]{new RemoveSuppressCommentFix(), new AllowSuppressionsFix()}
             : new InspectionGadgetsFix[]{new RemoveSuppressCommentFix()};
    }
    return InspectionGadgetsFix.EMPTY_ARRAY;
  }

  private static class RemoveSuppressCommentFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement != null) {
        psiElement.delete();
      }
    }

    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove //" + SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME;
    }
  }

  private class AllowSuppressionsFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      final Iterable<String> ids;
      if (psiElement instanceof PsiAnnotation) {
        ids = JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation((PsiModifierList)psiElement.getParent());
      }
      else {
        final String suppressedIds = JavaSuppressionUtil.getSuppressedInspectionIdsIn(psiElement);
        if (suppressedIds == null) {
          return;
        }
        ids = StringUtil.tokenize(suppressedIds, ",");
      }
      for (String id : ids) {
        if (!myAllowedSuppressions.contains(id)) {
          myAllowedSuppressions.add(id);
        }
      }
      saveProfile(project);
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
  }
}
