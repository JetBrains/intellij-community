/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

final class MissingDeprecatedAnnotationInspection extends BaseInspection implements CleanupLocalInspectionTool {
  @SuppressWarnings("PublicField") public boolean warnOnMissingJavadoc = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean annotationWarning = infos[0] == Boolean.TRUE;
    return annotationWarning
           ? InspectionGadgetsBundle.message("missing.deprecated.annotation.problem.descriptor")
           : InspectionGadgetsBundle.message("missing.deprecated.tag.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("warnOnMissingJavadoc", InspectionGadgetsBundle.message("missing.deprecated.tag.option")));
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos[0] == null && !ApplicationManager.getApplication().isUnitTestMode()) return null;
    final boolean annotationWarning = infos[0] == Boolean.TRUE;
    return annotationWarning ? new MissingDeprecatedAnnotationFix() : new MissingDeprecatedTagFix();
  }

  private static class MissingDeprecatedAnnotationFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("missing.deprecated.annotation.add.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement identifier = descriptor.getPsiElement();
      final PsiModifierListOwner parent = (PsiModifierListOwner)identifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation annotation = factory.createAnnotationFromText("@java.lang.Deprecated", parent);
      final PsiModifierList modifierList = parent.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.addAfter(annotation, null);
    }
  }

  private static class MissingDeprecatedTagFix extends InspectionGadgetsFix {
    @NonNls private static final String DEPRECATED_TAG_NAME = "deprecated";
    private static final String TEXT = " TODO: explain";

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("missing.add.deprecated.javadoc.tag.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement parent = descriptor.getPsiElement().getParent();
      if (!(parent instanceof PsiJavaDocumentedElement documentedElement)) {
        return;
      }
      PsiDocComment docComment = documentedElement.getDocComment();
      if (docComment != null) {
        PsiDocTag existingTag = docComment.findTagByName(DEPRECATED_TAG_NAME);
        PsiDocTag deprecatedTag = JavaPsiFacade.getElementFactory(project).createDocTagFromText("@" + DEPRECATED_TAG_NAME + TEXT);
        PsiDocTag addedTag = existingTag != null
                             ? (PsiDocTag)existingTag.replace(deprecatedTag)
                             : (PsiDocTag)docComment.add(deprecatedTag);
        moveCaretAfter(addedTag);
      }
      else {
        @NlsSafe PsiDocComment newDocComment = JavaPsiFacade.getElementFactory(project).createDocCommentFromText(
          StringUtil.join("/**\n", " * ", "@" + DEPRECATED_TAG_NAME + TEXT, "\n */")
        );
        PsiDocComment addedComment = (PsiDocComment)documentedElement.addBefore(newDocComment, documentedElement.getFirstChild());
        PsiDocTag addedTag = addedComment.findTagByName(DEPRECATED_TAG_NAME);
        if (addedTag != null) {
          moveCaretAfter(addedTag);
        }
      }
    }

    private static void moveCaretAfter(PsiDocTag tag) {
      if (IntentionPreviewUtils.isPreviewElement(tag)) {
        return;
      }
      Editor editor = FileEditorManager.getInstance(tag.getProject()).getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      PsiElement sibling = tag.getNextSibling();
      if (sibling instanceof Navigatable navigatable) {
        navigatable.navigate(true);
      }
      PsiDocTagValue valueElement = tag.getValueElement();
      if (valueElement == null) {
        return;
      }
      int start = valueElement.getTextOffset();
      int end = tag.getTextOffset() + tag.getTextLength();
      editor.getSelectionModel().setSelection(start, end);
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MissingDeprecatedAnnotationVisitor();
  }

  private class MissingDeprecatedAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitModule(@NotNull PsiJavaModule module) {
      super.visitModule(module);
      if (hasDeprecatedAnnotation(module)) {
        if (warnOnMissingJavadoc && !hasDeprecatedComment(module, true)) {
          registerModuleError(module, isOnTheFly() ? Boolean.FALSE : null);
        }
      }
      else if (hasDeprecatedComment(module, false)) {
        registerModuleError(module, Boolean.TRUE);
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (hasDeprecatedAnnotation(aClass)) {
        if (warnOnMissingJavadoc && !hasDeprecatedComment(aClass, true)) {
          registerClassError(aClass, isOnTheFly() ? Boolean.FALSE : null);
        }
      }
      else if (hasDeprecatedComment(aClass, false)) {
        registerClassError(aClass, Boolean.TRUE);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (hasDeprecatedAnnotation(method)) {
        if (warnOnMissingJavadoc) {
          PsiMethod m = method;
          while (m != null) {
            if (hasDeprecatedComment(m, true)) {
              return;
            }
            m = MethodUtils.getSuper(m);
          }
          registerMethodError(method, isOnTheFly() ? Boolean.FALSE : null);
        }
      }
      else if (hasDeprecatedComment(method, false)) {
        registerMethodError(method, Boolean.TRUE);
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      if (hasDeprecatedAnnotation(field)) {
        if (warnOnMissingJavadoc && !hasDeprecatedComment(field, true)) {
          registerFieldError(field, isOnTheFly() ? Boolean.FALSE : null);
        }
      }
      else if (hasDeprecatedComment(field, false)) {
        registerFieldError(field, Boolean.TRUE);
      }
    }

    private static boolean hasDeprecatedAnnotation(PsiModifierListOwner element) {
      final PsiModifierList modifierList = element.getModifierList();
      return modifierList != null && modifierList.hasAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED);
    }

    private static boolean hasDeprecatedComment(PsiJavaDocumentedElement documentedElement, boolean checkContent) {
      final PsiDocComment comment = documentedElement.getDocComment();
      if (comment == null) {
        return false;
      }
      final PsiDocTag deprecatedTag = comment.findTagByName("deprecated");
      if (deprecatedTag == null) {
        return false;
      }
      if (!checkContent) {
        return true;
      }
      for (PsiElement element : deprecatedTag.getDataElements()) {
        if (element instanceof PsiDocTagValue ||
            element instanceof PsiDocToken && ((PsiDocToken)element).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          return true;
        }
      }
      return false;
    }
  }
}