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
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MissingDeprecatedAnnotationInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean warnOnMissingJavadoc = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("missing.deprecated.annotation.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean annotationWarning = ((Boolean)infos[0]).booleanValue();
    return annotationWarning
           ? InspectionGadgetsBundle.message("missing.deprecated.annotation.problem.descriptor")
           : InspectionGadgetsBundle.message("missing.deprecated.tag.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("missing.deprecated.tag.option"),
                                          this, "warnOnMissingJavadoc");
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean annotationWarning = ((Boolean)infos[0]).booleanValue();
    if (!annotationWarning) {
      return null;
    }
    return new MissingDeprecatedAnnotationFix();
  }

  private static class MissingDeprecatedAnnotationFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("missing.deprecated.annotation.add.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
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

  @Override
  public boolean shouldInspect(PsiFile file) {
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
          registerModuleError(module, Boolean.FALSE);
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
          registerClassError(aClass, Boolean.FALSE);
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
          registerMethodError(method, Boolean.FALSE);
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
          registerFieldError(field, Boolean.FALSE);
        }
      }
      else if (hasDeprecatedComment(field, false)) {
        registerFieldError(field, Boolean.TRUE);
      }
    }

    private boolean hasDeprecatedAnnotation(PsiModifierListOwner element) {
      final PsiModifierList modifierList = element.getModifierList();
      return modifierList != null && modifierList.hasAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED);
    }

    private boolean hasDeprecatedComment(PsiJavaDocumentedElement element, boolean checkContent) {
      final PsiDocComment comment = element.getDocComment();
      if (comment == null) {
        return false;
      }
      final PsiDocTag deprecatedTag = comment.findTagByName("deprecated");
      if (deprecatedTag == null) {
        return false;
      }
      return !checkContent || deprecatedTag.getValueElement() != null;
    }
  }
}