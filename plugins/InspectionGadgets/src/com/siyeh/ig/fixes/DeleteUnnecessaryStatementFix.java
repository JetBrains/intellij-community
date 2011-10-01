/*
 * Copyright 2003-2005 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DeleteUnnecessaryStatementFix extends InspectionGadgetsFix {

  private final String name;

  public DeleteUnnecessaryStatementFix(@NonNls String name) {
    this.name = name;
  }

  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message(
      "smth.unnecessary.remove.quickfix", name);
  }

  protected void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    final PsiElement keywordElement = descriptor.getPsiElement();
    final PsiStatement statement = PsiTreeUtil.getParentOfType(keywordElement,
                                                               PsiStatement.class);
    if (statement == null) {
      return;
    }
    final PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiWhileStatement ||
        parent instanceof PsiDoWhileStatement ||
        parent instanceof PsiForeachStatement ||
        parent instanceof PsiForStatement) {
      replaceStatement(statement, "{}");
    }
    else {
      deleteElement(statement);
    }
  }
}