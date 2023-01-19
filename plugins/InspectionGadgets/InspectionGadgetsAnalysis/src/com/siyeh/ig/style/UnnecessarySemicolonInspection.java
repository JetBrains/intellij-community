/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;

public class UnnecessarySemicolonInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean ignoreAfterEnumConstants = false;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAfterEnumConstants", InspectionGadgetsBundle.message("unnecessary.semicolon.ignore.after.enum.constants.option")));
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.semicolon.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarySemicolonVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessarySemicolonFix();
  }

  private static class UnnecessarySemicolonFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.semicolon.remove.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement semicolonElement = descriptor.getPsiElement();
      if (semicolonElement instanceof PsiFile) return;
      final PsiElement parent = semicolonElement.getParent();
      if (parent instanceof PsiEmptyStatement) {
        final PsiElement lastChild = parent.getLastChild();
        if (lastChild instanceof PsiComment) {
          parent.replace(lastChild);
        }
        else {
          deleteElement(parent);
        }
      }
      else {
        deleteElement(semicolonElement);
      }
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !LanguageUtil.isInTemplateLanguageFile(file);
  }

  private class UnnecessarySemicolonVisitor extends BaseInspectionVisitor {
    @Override
    public void visitFile(@NotNull PsiFile file) {
      checkTopLevelSemicolons(file);
      super.visitFile(file);
    }

    @Override
    public void visitImportList(@NotNull PsiImportList list) {
      checkTopLevelSemicolons(list);
      super.visitImportList(list);
    }

    @Override
    public void visitModule(@NotNull PsiJavaModule module) {
      checkTopLevelSemicolons(module);
      super.visitModule(module);
    }

    private void checkTopLevelSemicolons(PsiElement element) {
      for (PsiElement sibling = element.getFirstChild(); sibling != null; sibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(sibling)) {
        if (sibling instanceof PsiErrorElement) return;
        if (PsiUtil.isJavaToken(sibling, JavaTokenType.SEMICOLON)) {
          registerError(sibling);
        }
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (!aClass.isEnum()) {
        checkTopLevelSemicolons(aClass);
      }
      else {
        boolean semicolonSeen = false;
        for (PsiElement sibling = aClass.getFirstChild(); sibling != null; sibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(sibling)) {
          if (sibling instanceof PsiErrorElement) return;
          if (PsiUtil.isJavaToken(sibling, JavaTokenType.SEMICOLON)) {
            if (semicolonSeen) {
              registerError(sibling);
            }
            else if (!ignoreAfterEnumConstants) {
              final PsiElement element = PsiTreeUtil.skipWhitespacesAndCommentsForward(sibling);
              if (PsiUtil.isJavaToken(element, JavaTokenType.RBRACE)) {
                registerError(sibling);
              }
            }
            semicolonSeen = true;
          }
        }
      }
    }

    @Override
    public void visitEmptyStatement(@NotNull PsiEmptyStatement statement) {
      super.visitEmptyStatement(statement);
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        final PsiElement semicolon = statement.getFirstChild();
        if (semicolon == null) {
          return;
        }
        registerError(semicolon);
      }
    }

    @Override
    public void visitResourceList(final @NotNull PsiResourceList resourceList) {
      super.visitResourceList(resourceList);
      final PsiElement last = resourceList.getLastChild();
      if (PsiUtil.isJavaToken(last, JavaTokenType.RPARENTH)) {
        final PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(last);
        if (PsiUtil.isJavaToken(prev, JavaTokenType.SEMICOLON)) {
          registerError(prev);
        }
      }
    }
  }
}