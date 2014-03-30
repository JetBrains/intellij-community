/*
 * Copyright 2009-2011 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryInheritDocInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.inherit.doc.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.inherit.doc.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryInheritDocFix();
  }

  private static class UnnecessaryInheritDocFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.inherit.doc.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiDocTag)) {
        return;
      }
      final PsiDocTag docTag = (PsiDocTag)element;
      final PsiDocComment docComment = docTag.getContainingComment();
      docComment.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInheritDocVisitor();
  }

  private static class UnnecessaryInheritDocVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitDocTag(PsiDocTag tag) {
      if (!(tag instanceof PsiInlineDocTag)) {
        return;
      }
      @NonNls final String name = tag.getName();
      if (!"inheritDoc".equals(name)) {
        return;
      }
      final PsiDocComment docComment = tag.getContainingComment();
      if (docComment == null) {
        return;
      }
      final PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(
        docComment, PsiDocToken.class);
      if (docTokens == null) {
        return;
      }
      for (PsiDocToken docToken : docTokens) {
        final IElementType tokenType = docToken.getTokenType();
        if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
          continue;
        }
        if (!StringUtil.isEmptyOrSpaces(docToken.getText())) {
          return;
        }
      }
      registerError(tag);
    }
  }
}
