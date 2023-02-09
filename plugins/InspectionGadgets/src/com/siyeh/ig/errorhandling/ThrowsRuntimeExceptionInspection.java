/*
 * Copyright 2011-2018 Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ThrowsRuntimeExceptionInspection extends BaseInspection {

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final String exceptionName = (String)infos[0];
    if (MoveExceptionToJavadocFix.isApplicable((PsiJavaCodeReferenceElement)infos[1])) {
      return new InspectionGadgetsFix[] {
        new ThrowsRuntimeExceptionFix(exceptionName),
        new MoveExceptionToJavadocFix(exceptionName)
      };
    }
    return new InspectionGadgetsFix[] {new ThrowsRuntimeExceptionFix(exceptionName)};
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("throws.runtime.exception.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowsRuntimeExceptionVisitor();
  }

  private static class MoveExceptionToJavadocFix extends InspectionGadgetsFix {

    private final String myExceptionName;

    MoveExceptionToJavadocFix(String exceptionName) {
      myExceptionName = exceptionName;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("throws.runtime.exception.move.quickfix", myExceptionName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("move.exception.to.javadoc.fix.family.name");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethod method)) {
        return;
      }
      final PsiDocComment comment = method.getDocComment();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (comment != null) {
        final PsiDocTag docTag = factory.createDocTagFromText("@throws " + element.getText());
        comment.add(docTag);
      }
      else {
        final PsiDocComment docComment = factory.createDocCommentFromText("/** */");
        final PsiComment resultComment = (PsiComment)method.addBefore(docComment, method.getModifierList());
        final DocumentationProvider documentationProvider = LanguageDocumentation.INSTANCE.forLanguage(method.getLanguage());
        final CodeDocumentationProvider codeDocumentationProvider;
        if (documentationProvider instanceof CodeDocumentationProvider) {
          codeDocumentationProvider = (CodeDocumentationProvider)documentationProvider;
        }
        else if (documentationProvider instanceof CompositeDocumentationProvider compositeDocumentationProvider) {
          codeDocumentationProvider = compositeDocumentationProvider.getFirstCodeDocumentationProvider();
          if (codeDocumentationProvider == null) {
            return;
          }
        }
        else {
          return;
        }
        final String commentStub = codeDocumentationProvider.generateDocumentationContentStub(resultComment);
        final PsiDocComment newComment = factory.createDocCommentFromText("/**\n" + commentStub + "*/");
        resultComment.replace(newComment);
      }
      element.delete();
    }

    public static boolean isApplicable(@NotNull PsiJavaCodeReferenceElement reference) {
      final PsiElement parent = reference.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethod method)) {
        return false;
      }
      final PsiDocComment docComment = method.getDocComment();
      if (docComment == null) {
        return true;
      }
      final PsiElement throwsTarget = reference.resolve();
      if (throwsTarget == null) {
        return true;
      }
      final PsiDocTag[] tags = docComment.findTagsByName("throws");
      for (PsiDocTag tag : tags) {
        final PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement == null) {
          continue;
        }
        final PsiElement child = valueElement.getFirstChild();
        if (child == null) {
          continue;
        }
        final PsiElement grandChild = child.getFirstChild();
        if (!(grandChild instanceof PsiJavaCodeReferenceElement referenceElement)) {
          continue;
        }
        final PsiElement target = referenceElement.resolve();
        if (throwsTarget.equals(target)) {
          return false;
        }
      }
      return true;
    }
  }

  private static class ThrowsRuntimeExceptionFix extends InspectionGadgetsFix {

    private final String myClassName;

    ThrowsRuntimeExceptionFix(String className) {
      myClassName = className;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("throws.runtime.exception.quickfix", myClassName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("throws.runtime.exception.fix.family.name");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      descriptor.getPsiElement().delete();
    }
  }

  private static class ThrowsRuntimeExceptionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        final PsiElement target = referenceElement.resolve();
        if (!(target instanceof PsiClass aClass)) {
          continue;
        }
        if (!InheritanceUtil.isInheritor(aClass, "java.lang.RuntimeException")) {
          continue;
        }
        final String className = aClass.getName();
        registerError(referenceElement, className, referenceElement);
      }
    }
  }
}
