/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.enumswitch;

import com.intellij.psi.*;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CreateEnumSwitchBranchesIntention extends Intention {

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new EnumSwitchPredicate();
  }

  public void processIntention(@NotNull PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    final PsiSwitchStatement switchStatement = (PsiSwitchStatement)element;
    final PsiCodeBlock body = switchStatement.getBody();
    final PsiExpression switchExpression = switchStatement.getExpression();
    if (switchExpression == null) {
      return;
    }
    final PsiClassType switchType = (PsiClassType)switchExpression.getType();
    if (switchType == null) {
      return;
    }
    final PsiClass enumClass = switchType.resolve();
    if (enumClass == null) {
      return;
    }
    final PsiField[] fields = enumClass.getFields();
    final List<String> missingEnumElements = new ArrayList<String>(fields.length);
    for (final PsiField field : fields) {
      if (field instanceof PsiEnumConstant) {
        missingEnumElements.add(field.getName());
      }
    }
    if (body != null) {
      final PsiStatement[] statements = body.getStatements();
      for (final PsiStatement statement : statements) {
        if (!(statement instanceof PsiSwitchLabelStatement)) {
          continue;
        }
        final PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)statement;
        final PsiExpression value = labelStatement.getCaseValue();
        if (!(value instanceof PsiReferenceExpression)) {
          continue;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression)value;
        final PsiElement resolved = reference.resolve();
        if (!(resolved instanceof PsiEnumConstant)) {
          continue;
        }
        final PsiEnumConstant enumConstant = (PsiEnumConstant)resolved;
        missingEnumElements.remove(enumConstant.getName());
      }
    }
    @NonNls final StringBuilder newStatementText = new StringBuilder();
    newStatementText.append("switch(").append(switchExpression.getText()).append("){");
    if (body != null) {
      int position = 0;
      final PsiElement[] children = body.getChildren();
      for (position = 1; position < children.length - 1; position++) {
        final PsiElement child = children[position];
        if (child instanceof PsiSwitchLabelStatement) {
          final PsiSwitchLabelStatement switchLabelStatement = (PsiSwitchLabelStatement)child;
          if (switchLabelStatement.isDefaultCase()) {
            break;
          }
        }
        newStatementText.append(child.getText());
      }
      appendMissingEnumCases(missingEnumElements, newStatementText);
      for (; position< children.length - 1; position++) {
        newStatementText.append(children[position].getText());
      }
    }
    else {
      appendMissingEnumCases(missingEnumElements, newStatementText);
    }
    newStatementText.append('}');
    replaceStatement(newStatementText.toString(), switchStatement);
  }

  private static void appendMissingEnumCases(List<String> missingEnumElements, @NonNls StringBuilder newStatementText) {
    for (String missingEnumElement : missingEnumElements) {
      newStatementText.append("case ").append(missingEnumElement).append(": break;");
    }
  }
}