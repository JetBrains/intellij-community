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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class NormalizeDeclarationFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("normalize.declaration.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement variableNameElement = descriptor.getPsiElement();
    final PsiVariable parent =
      (PsiVariable)variableNameElement.getParent();
    if (parent == null) {
      return;
    }
    if (parent instanceof PsiField) {
      parent.normalizeDeclaration();
      return;
    }
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiDeclarationStatement)) {
      return;
    }
    final PsiElement greatGrandParent = grandParent.getParent();
    if (greatGrandParent instanceof PsiForStatement) {
      final PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)grandParent;
      splitMultipleDeclarationInForStatementInitialization(
        declarationStatement);
      return;
    }
    parent.normalizeDeclaration();
  }

  private static void splitMultipleDeclarationInForStatementInitialization(
    PsiDeclarationStatement declarationStatement) {
    final PsiElement forStatement = declarationStatement.getParent();
    final PsiElement[] declaredElements =
      declarationStatement.getDeclaredElements();
    final Project project = forStatement.getProject();
    final PsiElementFactory factory =
      JavaPsiFacade.getElementFactory(project);
    final PsiElement greatGreatGrandParent = forStatement.getParent();
    final PsiBlockStatement blockStatement;
    final PsiCodeBlock codeBlock;
    if (!(greatGreatGrandParent instanceof PsiCodeBlock)) {
      blockStatement = (PsiBlockStatement)
        factory.createStatementFromText("{}", forStatement);
      codeBlock = blockStatement.getCodeBlock();
    }
    else {
      blockStatement = null;
      codeBlock = null;
    }
    for (int i = 1; i < declaredElements.length; i++) {
      final PsiElement declaredElement = declaredElements[i];
      if (!(declaredElement instanceof PsiVariable)) {
        continue;
      }
      final PsiVariable variable = (PsiVariable)declaredElement;
      final PsiType type = variable.getType();
      final String typeText = type.getCanonicalText();
      final StringBuilder newStatementText =
        new StringBuilder(typeText);
      newStatementText.append(' ');
      newStatementText.append(variable.getName());
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        newStatementText.append('=');
        newStatementText.append(initializer.getText());
      }
      newStatementText.append(';');
      final PsiStatement newStatement =
        factory.createStatementFromText(
          newStatementText.toString(), forStatement);
      if (codeBlock == null) {
        greatGreatGrandParent.addBefore(newStatement, forStatement);
      }
      else {
        codeBlock.add(newStatement);
      }
    }
    for (int i = 1; i < declaredElements.length; i++) {
      final PsiElement declaredElement = declaredElements[i];
      if (!(declaredElement instanceof PsiVariable)) {
        continue;
      }
      declaredElement.delete();
    }
    if (codeBlock != null) {
      codeBlock.add(forStatement);
      forStatement.replace(blockStatement);
    }
  }
}
