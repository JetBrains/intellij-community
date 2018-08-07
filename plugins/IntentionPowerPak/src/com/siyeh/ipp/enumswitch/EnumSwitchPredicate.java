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
package com.siyeh.ipp.enumswitch;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

import java.util.HashSet;
import java.util.Set;

class EnumSwitchPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling instanceof PsiSwitchStatement && ErrorUtil.containsError(prevSibling)) {
        element = prevSibling;
      }
    }
    if (!(element instanceof PsiSwitchStatement)) {
      return false;
    }
    final PsiSwitchStatement switchStatement = (PsiSwitchStatement)element;
    final PsiExpression expression = switchStatement.getExpression();
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClass enumClass = ((PsiClassType)type).resolve();
    if (enumClass == null || !enumClass.isEnum()) {
      return false;
    }
    final PsiField[] fields = enumClass.getFields();
    if (fields.length == 0) {
      return false;
    }
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return true;
    }
    final Set<String> enumElements = new HashSet<>(fields.length);
    for (final PsiField field : fields) {
      if (field instanceof PsiEnumConstant) {
        enumElements.add(field.getName());
      }
    }
    for (PsiStatement statement : body.getStatements()) {
      if (!(statement instanceof PsiSwitchLabelStatement)) {
        continue;
      }
      final PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)statement;
      final PsiExpression value = labelStatement.getCaseValue();
      if (value == null) {
        continue;
      }
      final String valueText = value.getText();
      enumElements.remove(valueText);
    }
    return !enumElements.isEmpty();
  }
}
