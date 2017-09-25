/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.fqnames;

import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nullable;

class FullyQualifiedNamePredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
    if (!referenceElement.isQualified()) {
      return false;
    }
    final PsiElement parent = referenceElement.getParent();
    if (parent instanceof PsiMethodCallExpression || parent instanceof PsiAssignmentExpression || parent instanceof PsiVariable) {
      return false;
    }
    if (PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class, PsiPackageStatement.class, JavaCodeFragment.class) != null) {
      return false;
    }
    final Project project = element.getProject();
    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
    if (isInsideCommentInPackageInfo(referenceElement)) {
      return false;
    }
    final PsiFile file = element.getContainingFile();
    if ("module-info.java".equals(file.getName())) {
      return false;
    }
    final PsiElement qualifier = referenceElement.getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiJavaCodeReferenceElement qualifierReferenceElement = (PsiJavaCodeReferenceElement)qualifier;
    final PsiElement resolved = qualifierReferenceElement.resolve();
    if (!(resolved instanceof PsiPackage)) {
      if (!(resolved instanceof PsiClass)) {
        return false;
      }
      if (!codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS) {
        return false;
      }
    }
    final PsiElement target = referenceElement.resolve();
    if (!(target instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)target;
    final String fqName = aClass.getQualifiedName();
    return fqName != null && ImportUtils.nameCanBeImported(fqName, element);
  }

  private static boolean isInsideCommentInPackageInfo(@Nullable PsiJavaCodeReferenceElement referenceElement) {
    final PsiDocComment containingComment = PsiTreeUtil.getParentOfType(referenceElement, PsiDocComment.class);
    return JavaDocUtil.isInsidePackageInfo(containingComment);
  }
}