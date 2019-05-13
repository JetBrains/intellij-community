/*
 * Copyright 2009-2018 Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryInheritDocInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.inherit.doc.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    switch ((WarningType)infos[0]) {
      case MODULE:
        return InspectionGadgetsBundle.message("unnecessary.inherit.doc.module.invalid.problem.descriptor");
      case CLASS:
        return InspectionGadgetsBundle.message("unnecessary.inherit.doc.class.invalid.problem.descriptor");
      case FIELD:
        return InspectionGadgetsBundle.message("unnecessary.inherit.doc.field.invalid.problem.descriptor");
      case CONSTRUCTOR:
        return InspectionGadgetsBundle.message("unnecessary.inherit.doc.constructor.invalid.problem.descriptor");
      case NO_SUPER:
        return InspectionGadgetsBundle.message("unnecessary.inherit.doc.constructor.no.super.problem.descriptor");
      case EMPTY:
        return InspectionGadgetsBundle.message("unnecessary.inherit.doc.problem.descriptor");
      default:
        throw new AssertionError();
    }
  }

  enum WarningType {
    MODULE, CLASS, FIELD, CONSTRUCTOR, EMPTY, NO_SUPER
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryInheritDocFix();
  }

  private static class UnnecessaryInheritDocFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.inherit.doc.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiDocTag)) {
        return;
      }
      final PsiDocTag docTag = (PsiDocTag)element;
      final PsiElement parent = docTag.getParent();
      if (parent instanceof PsiDocComment) {
        final PsiDocComment docComment = (PsiDocComment)parent;
        final PsiDocTag[] docTags = docComment.getTags();
        if (docTags.length > 0) {
          element.delete();
          return;
        }
        final PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(parent, PsiDocToken.class);
        if (docTokens != null) {
          for (PsiDocToken docToken : docTokens) {
            final IElementType tokenType = docToken.getTokenType();
            if (JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType) && !StringUtil.isEmptyOrSpaces(docToken.getText())) {
              element.delete();
              return;
            }
          }
        }
      }
      parent.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInheritDocVisitor();
  }

  private static class UnnecessaryInheritDocVisitor extends BaseInspectionVisitor {

    @Override
    public void visitInlineDocTag(PsiInlineDocTag tag) {
      @NonNls final String name = tag.getName();
      if (!"inheritDoc".equals(name)) {
        return;
      }
      final PsiDocComment docComment = tag.getContainingComment();
      if (docComment == null) {
        return;
      }
      final PsiJavaDocumentedElement owner = docComment.getOwner();
      if (owner instanceof PsiJavaModule) {
        registerError(tag, WarningType.MODULE);
        return;
      }
      if (owner instanceof PsiField) {
        registerError(tag, WarningType.FIELD);
        return;
      }
      else if (owner instanceof PsiClass) {
        registerError(tag, WarningType.CLASS);
        return;
      }
      else if (owner instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)owner;
        if (method.isConstructor()) {
          registerError(tag, WarningType.CONSTRUCTOR);
          return;
        }
        if (!MethodUtils.hasSuper(method)) {
          registerError(tag, WarningType.NO_SUPER);
          return;
        }
      }
      else {
        return;
      }
      final PsiElement parent = tag.getParent();
      if (parent instanceof PsiDocTag) {
        final PsiDocTag docTag = (PsiDocTag)parent;
        final String docTagName = docTag.getName();
        if ((docTagName.equals("throws") || docTagName.equals("exception")) &&
            !isCheckExceptionAndPresentInThrowsList((PsiMethod)owner, docTag)) {
          return;
        }
      }
      final PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(parent, PsiDocToken.class);
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
      registerError(tag, WarningType.EMPTY);
    }

    private static boolean isCheckExceptionAndPresentInThrowsList(PsiMethod method, PsiDocTag docTag) {
      final PsiDocTagValue valueElement = docTag.getValueElement();
      final PsiJavaCodeReferenceElement referenceElement =
        PsiTreeUtil.findChildOfType(valueElement, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        final PsiElement target = referenceElement.resolve();
        if (!(target instanceof PsiClass)) {
          return false;
        }
        final PsiClass aClass = (PsiClass)target;
        if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_EXCEPTION) ||
          InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
          return false;
        }
        final PsiReferenceList throwsList = method.getThrowsList();
        final PsiJavaCodeReferenceElement[] elements = throwsList.getReferenceElements();
        boolean found = false;
        for (PsiJavaCodeReferenceElement element : elements) {
          if (target.equals(element.resolve())) {
            found = true;
          }
        }
        if (!found){
          return false;
        }
      }
      return true;
    }
  }
}
