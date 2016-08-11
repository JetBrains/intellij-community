/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.BatchSuppressManager;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.SuppressionUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SuppressionAnnotationInspectionBase extends BaseInspection {
  public List<String> myAllowedSuppressions = new ArrayList<>();

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "inspection.suppression.annotation.display.name");
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

  @NotNull
  @Override
  public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuppressionAnnotationVisitor();
  }

  private class SuppressionAnnotationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitComment(PsiComment comment) {
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
    public void visitAnnotation(PsiAnnotation annotation) {
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
