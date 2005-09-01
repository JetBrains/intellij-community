/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessarySemicolonInspection extends ClassInspection {

  private final UnnecessarySemicolonFix fix = new UnnecessarySemicolonFix();

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarySemicolonVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnnecessarySemicolonFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.semicolon.remove.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement semicolonElement = descriptor.getPsiElement();
      deleteElement(semicolonElement);
    }
  }

  private static class UnnecessarySemicolonVisitor
    extends BaseInspectionVisitor {

    private boolean classAlreadyVisited;

    public void visitClass(@NotNull PsiClass aClass) {
      if (classAlreadyVisited) return;
      classAlreadyVisited = true;
      PsiElement sibling = skipForwardWhiteSpacesAndComments(aClass);
      while (sibling != null) {
        if (sibling instanceof PsiJavaToken &&
            ((PsiJavaToken)sibling).getTokenType()
              .equals(JavaTokenType.SEMICOLON)) {
          registerError(sibling);
        }
        else {
          break;
        }
        sibling = skipForwardWhiteSpacesAndComments(sibling);
      }

      //TODO: Dave, correct me if I'm wrong but I think that only semicolon after last member in enum is unneccessary
      //Also your indentation level differs from ours:)
      if (aClass.isEnum()) {
        final PsiField[] fields = aClass.getFields();
        if (fields.length > 0) {
          final PsiField last = fields[fields.length - 1];
          if (last instanceof PsiEnumConstant) {
            final PsiElement element = skipForwardWhiteSpacesAndComments(last);
            if (element instanceof PsiJavaToken &&
                ((PsiJavaToken)element).getTokenType()
                  .equals(JavaTokenType.SEMICOLON)) {
              final PsiElement next = skipForwardWhiteSpacesAndComments(element);
              if (next == null || next == aClass.getRBrace()) {
                registerError(element);
              }
            }
          }
        }
      }
      super.visitClass(aClass);
    }

    private static
    @Nullable
    PsiElement skipForwardWhiteSpacesAndComments(PsiElement element) {
      return PsiTreeUtil.skipSiblingsForward(element,
                                             new Class[]{
                                               PsiWhiteSpace.class,
                                               PsiComment.class});
    }

    public void visitEmptyStatement(PsiEmptyStatement statement) {
      super.visitEmptyStatement(statement);
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        registerError(statement);
      }
    }
  }
}
