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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction;
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
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) { 
    if (infos.length == 1) {
      if (infos[0] instanceof PsiAnnotation) {
        final PsiAnnotation annotation = (PsiAnnotation)infos[0];
        PsiElement parent = annotation.getParent();
        final Collection<String> ids = JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation((PsiModifierList)parent);
        if (!ids.isEmpty()) {
          return new InspectionGadgetsFix[]{new DelegatingFix(new RemoveAnnotationQuickFix(annotation, null)), new AllowSuppressionsFix()};
        }
      } else if (infos[0] instanceof PsiComment) {
        return new InspectionGadgetsFix[]{new RemoveSuppressCommentFix(), new AllowSuppressionsFix()};
      }
    }
    return InspectionGadgetsFix.EMPTY_ARRAY;
  }

  private static class RemoveSuppressCommentFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement != null) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(psiElement)) return;
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
      final String suppressedIds = JavaSuppressionUtil.getSuppressedInspectionIdsIn(psiElement);
      final Iterable<String> ids = suppressedIds != null ? StringUtil.tokenize(suppressedIds, "[, ]") : null;
      if (ids != null) {
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
  }
}
