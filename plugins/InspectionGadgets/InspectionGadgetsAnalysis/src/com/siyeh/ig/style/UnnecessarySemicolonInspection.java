/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UnnecessarySemicolonInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean ignoreAfterEnumConstants = false;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unnecessary.semicolon.ignore.after.enum.constants.option"),
                                          this, "ignoreAfterEnumConstants");
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
    public void doFix(Project project, ProblemDescriptor descriptor) {
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
  public boolean shouldInspect(PsiFile file) {
    return !LanguageUtil.isInTemplateLanguageFile(file);
  }

  private class UnnecessarySemicolonVisitor extends BaseInspectionVisitor {
    @Override
    public void visitFile(@NotNull PsiFile file) {
      findTopLevelSemicolons(file);
      super.visitFile(file);
    }

    @Override
    public void visitImportList(PsiImportList list) {
      findTopLevelSemicolons(list);
      super.visitImportList(list);
    }

    @Override
    public void visitModule(PsiJavaModule module) {
      findTopLevelSemicolons(module);
      super.visitModule(module);
    }

    private void findTopLevelSemicolons(PsiElement element) {
      for (PsiElement sibling = element.getFirstChild(); sibling != null; sibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(sibling)) {
        if (sibling instanceof PsiErrorElement) return;
        if (PsiUtil.isJavaToken(sibling, JavaTokenType.SEMICOLON)) {
          registerError(sibling, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);

      findUnnecessarySemicolonsAfterEnumConstants(aClass);
      if (!aClass.isEnum() || ignoreAfterEnumConstants) {
        return;
      }
      final PsiField[] fields = aClass.getFields();
      final PsiElement element;
      if (fields.length > 0) {
        final PsiField lastField = fields[fields.length - 1];
        if (!(lastField instanceof PsiEnumConstant)) {
          return;
        }
        element = PsiTreeUtil.skipWhitespacesAndCommentsForward(lastField);
      }
      else {
        final PsiElement lBrace = aClass.getLBrace();
        element = PsiTreeUtil.skipWhitespacesAndCommentsForward(lBrace);
      }
      if (!(element instanceof PsiJavaToken)) {
        return;
      }
      final PsiJavaToken token = (PsiJavaToken)element;
      final IElementType tokenType = token.getTokenType();
      if (!tokenType.equals(JavaTokenType.SEMICOLON)) {
        return;
      }
      final PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(element);
      if (next == null || !next.equals(aClass.getRBrace())) {
        return;
      }
      registerError(element, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }

    private void findUnnecessarySemicolonsAfterEnumConstants(@NotNull PsiClass aClass) {
      PsiElement child = aClass.getFirstChild();
      while (child != null) {
        if (child instanceof PsiJavaToken) {
          final PsiJavaToken token = (PsiJavaToken)child;
          final IElementType tokenType = token.getTokenType();
          if (tokenType.equals(JavaTokenType.SEMICOLON)) {
            final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesAndCommentsBackward(child);
            if (!(prevSibling instanceof PsiEnumConstant)) {
              if (prevSibling instanceof PsiJavaToken) {
                final IElementType prevTokenType = ((PsiJavaToken)prevSibling).getTokenType();
                if (!JavaTokenType.COMMA.equals(prevTokenType) && !JavaTokenType.LBRACE.equals(prevTokenType)) {
                  registerError(child, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
                }
              }
              else {
                registerError(child, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
              }
            }
          }
        }
        child = PsiTreeUtil.skipWhitespacesAndCommentsForward(child);
      }
    }

    @Override
    public void visitEmptyStatement(PsiEmptyStatement statement) {
      super.visitEmptyStatement(statement);
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        final PsiElement semicolon = statement.getFirstChild();
        if (semicolon == null) {
          return;
        }
        registerError(semicolon, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      }
    }

    @Override
    public void visitResourceList(final PsiResourceList resourceList) {
      super.visitResourceList(resourceList);
      final PsiElement last = resourceList.getLastChild();
      if (PsiUtil.isJavaToken(last, JavaTokenType.RPARENTH)) {
        final PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(last);
        if (PsiUtil.isJavaToken(prev, JavaTokenType.SEMICOLON)) {
          registerError(prev, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }
      }
    }
  }
}