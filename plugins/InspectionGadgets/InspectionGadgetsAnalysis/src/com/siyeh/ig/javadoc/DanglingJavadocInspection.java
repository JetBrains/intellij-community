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
package com.siyeh.ig.javadoc;

import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DanglingJavadocInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("dangling.javadoc.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("dangling.javadoc.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[] {
      new DeleteCommentFix(),
      new ConvertCommentFix()
    };
  }

  private static class ConvertCommentFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("dangling.javadoc.convert.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement docComment = element.getParent();
      final StringBuilder newCommentText = new StringBuilder();
      for (PsiElement child : docComment.getChildren()) {
        if (child instanceof PsiDocToken) {
          final PsiDocToken docToken = (PsiDocToken)child;
          final IElementType tokenType = docToken.getTokenType();
          if (JavaDocTokenType.DOC_COMMENT_START.equals(tokenType)) {
            newCommentText.append("/*");
          }
          else if (!JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS.equals(tokenType)) {
            newCommentText.append(child.getText());
          }
        }
        else {
          newCommentText.append(child.getText());
        }
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiComment newComment = factory.createCommentFromText(newCommentText.toString(), element);
      docComment.replace(newComment);
    }
  }

  private static class DeleteCommentFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("dangling.javadoc.delete.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      element.getParent().delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DanglingJavadocVisitor();
  }

  private static class DanglingJavadocVisitor extends BaseInspectionVisitor {

    @Override
    public void visitDocComment(PsiDocComment comment) {
      super.visitDocComment(comment);
      if (comment.getOwner() != null) {
        return;
      }
      if (JavaDocUtil.isInsidePackageInfo(comment) &&
          PsiTreeUtil.skipWhitespacesForward(comment) instanceof PsiPackageStatement &&
          "package-info.java".equals(comment.getContainingFile().getName())) {
        return;
      }
      registerError(comment.getFirstChild());
    }
  }
}
