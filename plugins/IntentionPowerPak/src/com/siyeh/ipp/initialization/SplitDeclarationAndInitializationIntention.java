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
package com.siyeh.ipp.initialization;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

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
    final boolean fieldIsStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiClassInitializer[] classInitializers = containingClass.getInitializers();
    PsiClassInitializer classInitializer = null;
    final int fieldOffset = field.getTextOffset();
    for (PsiClassInitializer existingClassInitializer : classInitializers) {
      final int initializerOffset = existingClassInitializer.getTextOffset();
      if (initializerOffset <= fieldOffset) {
        continue;
      }
      final boolean initializerIsStatic = existingClassInitializer.hasModifierProperty(PsiModifier.STATIC);
      if (initializerIsStatic == fieldIsStatic) {
        Predicate<PsiReference> usedBeforeInitializer = ref -> {
          PsiElement refElement = ref.getElement();
          if (refElement == null) {
            return true;
          }
          TextRange textRange = refElement.getTextRange();
          return textRange == null || textRange.getStartOffset() < initializerOffset;
        };
        if (ReferencesSearch.search(field, new LocalSearchScope(containingClass)).findAll().stream().noneMatch(usedBeforeInitializer)) {
          classInitializer = existingClassInitializer;
          break;
        }
      }
    }
    final PsiManager manager = field.getManager();
    final Project project = manager.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    if (classInitializer == null) {
      if (PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesForward(field), JavaTokenType.COMMA)) {
        field.normalizeDeclaration();
      }
      classInitializer = (PsiClassInitializer)containingClass.addAfter(elementFactory.createClassInitializer(), field);

      // add some whitespace between the field and the class initializer
      final PsiElement whitespace = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n");
      containingClass.addAfter(whitespace, field);
    }
    final PsiCodeBlock body = classInitializer.getBody();
    @NonNls final String initializationStatementText = field.getName() + " = " + initializerText + ';';
    final PsiExpressionStatement statement = (PsiExpressionStatement)elementFactory.createStatementFromText(initializationStatementText, body);
    final PsiElement addedElement = body.addAfter(statement, null);
    if (fieldIsStatic) {
      final PsiModifierList modifierList = classInitializer.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
      }
    }
    initializer.delete();
    CodeStyleManager.getInstance(manager.getProject()).reformat(classInitializer);
    HighlightUtil.highlightElement(addedElement, IntentionPowerPackBundle.message(
                                     "press.escape.to.remove.highlighting.message"));
  }
}