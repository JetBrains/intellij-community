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
package com.siyeh.ig.maturity;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SuppressionAnnotationInspection extends BaseInspection {
  public List<String> myAllowedSuppressions = new ArrayList<>();

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm(JavaBundle.message("column.name.ignore.suppressions"), JavaBundle.message("ignored.suppressions"), myAllowedSuppressions);
    final JComponent contentPanel = form.getContentPanel();
    contentPanel.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
    return contentPanel;
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
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

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "inspection.suppression.annotation.problem.descriptor");
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuppressionAnnotationVisitor();
  }

  private static class RemoveSuppressCommentFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement != null) {
        psiElement.delete();
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.suppress.comment.fix.family.name", SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
    }
  }

  private class AllowSuppressionsFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return new IntentionPreviewInfo.Html(HtmlChunk.text(InspectionGadgetsBundle.message("allow.suppressions.preview.text")));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("allow.suppressions.fix.text");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("allow.suppressions.fix.family.name");
    }
  }

  private class SuppressionAnnotationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitComment(@NotNull PsiComment comment) {
      super.visitComment(comment);
      final IElementType tokenType = comment.getTokenType();
      if (!tokenType.equals(JavaTokenType.END_OF_LINE_COMMENT)
          && !tokenType.equals(JavaTokenType.C_STYLE_COMMENT)) {
        return;
      }
      final String commentText = comment.getText();
      if (commentText.length() <= 2) {
        return;
      }
      @NonNls final String strippedComment = commentText.substring(2).trim();
      if (!strippedComment.startsWith(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)) {
        return;
      }
      final String suppressedIds = JavaSuppressionUtil.getSuppressedInspectionIdsIn(comment);
      if (suppressedIds == null) {
        registerError(comment, comment, Boolean.FALSE);
        return;
      }
      final Iterable<String> ids = StringUtil.tokenize(suppressedIds, ",");
      for (String id : ids) {
        if (!myAllowedSuppressions.contains(id)) {
          registerError(comment, comment, Boolean.TRUE);
          break;
        }
      }
    }

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      final PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
      if (reference == null) {
        return;
      }
      @NonNls final String text = reference.getText();
      if ("SuppressWarnings".equals(text) ||
          BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME.equals(text)) {
        final PsiElement annotationParent = annotation.getParent();
        if (annotationParent instanceof PsiModifierList) {
          final Collection<String> ids = JavaSuppressionUtil.getInspectionIdsSuppressedInAnnotation((PsiModifierList)annotationParent);
          if (!myAllowedSuppressions.containsAll(ids)) {
            registerError(annotation, annotation, Boolean.TRUE);
          }
          else if (ids.isEmpty()) {
            registerError(annotation, annotation, Boolean.FALSE);
          }
        }
      }
    }
  }
}
