/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.initialization;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SplitDeclarationAndInitializationIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SplitDeclarationAndInitializationPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiField field = (PsiField)element.getParent();
    final PsiExpression initializer = field.getInitializer();
    if (initializer == null) {
      return;
    }
    final String initializerText = RefactoringUtil.convertInitializerToNormalExpression(initializer, field.getType()).getText();
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) {
      return;
    }
    final boolean fieldIsStatic =
      field.hasModifierProperty(PsiModifier.STATIC);
    final PsiClassInitializer[] classInitializers =
      containingClass.getInitializers();
    PsiClassInitializer classInitializer = null;
    final int fieldOffset = field.getTextOffset();
    for (PsiClassInitializer existingClassInitializer : classInitializers) {
      final int initializerOffset =
        existingClassInitializer.getTextOffset();
      if (initializerOffset <= fieldOffset) {
        continue;
      }
      final boolean initializerIsStatic =
        existingClassInitializer.hasModifierProperty(
          PsiModifier.STATIC);
      if (initializerIsStatic == fieldIsStatic) {
        classInitializer = existingClassInitializer;
        break;
      }
    }
    final PsiManager manager = field.getManager();
    final Project project = manager.getProject();
    final PsiElementFactory elementFactory =
      JavaPsiFacade.getInstance(project).getElementFactory();
    if (classInitializer == null) {
      classInitializer = elementFactory.createClassInitializer();
      classInitializer = (PsiClassInitializer)
        containingClass.addAfter(classInitializer, field);

      // add some whitespace between the field and the class initializer
      final PsiElement whitespace =
        PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n");
      containingClass.addAfter(whitespace, field);
    }
    final PsiCodeBlock body = classInitializer.getBody();
    @NonNls final String initializationStatementText =
      field.getName() + " = " + initializerText + ';';
    final PsiExpressionStatement statement =
      (PsiExpressionStatement)elementFactory.createStatementFromText(
        initializationStatementText, body);
    final PsiElement addedElement = body.add(statement);
    if (fieldIsStatic) {
      final PsiModifierList modifierList =
        classInitializer.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
      }
    }
    initializer.delete();
    CodeStyleManager.getInstance(manager.getProject()).reformat(classInitializer);
    HighlightUtil.highlightElement(addedElement,
                                   IntentionPowerPackBundle.message(
                                     "press.escape.to.remove.highlighting.message"));
  }
}