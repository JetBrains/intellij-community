/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateEnumSwitchBranchesIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new EnumSwitchPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
      assert element != null;
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
    final List<PsiEnumConstant> missingEnumElements = new ArrayList<>(fields.length);
    for (final PsiField field : fields) {
      if (!(field instanceof PsiEnumConstant)) {
        continue;
      }
      final PsiEnumConstant enumConstant = (PsiEnumConstant)field;
      missingEnumElements.add(enumConstant);
    }
    if (body == null) {
      // replace entire switch statement if no code block is present
      @NonNls final StringBuilder newStatementText = new StringBuilder();
      newStatementText.append("switch(").append(switchExpression.getText()).append("){");
      for (PsiEnumConstant missingEnumElement : missingEnumElements) {
        newStatementText.append("case ").append(missingEnumElement.getName()).append(": break;");
      }
      newStatementText.append('}');
      PsiReplacementUtil.replaceStatement(switchStatement, newStatementText.toString());
      return;
    }
    final Map<PsiEnumConstant, PsiEnumConstant> nextEnumConstants = new HashMap<>(fields.length);
    PsiEnumConstant previous = null;
    for (PsiEnumConstant enumConstant : missingEnumElements) {
      if (previous != null) {
        nextEnumConstants.put(previous, enumConstant);
      }
      previous = enumConstant;
    }
    final PsiStatement[] statements = body.getStatements();
    for (final PsiStatement statement : statements) {
      missingEnumElements.remove(findEnumConstant(statement));
    }
    PsiEnumConstant nextEnumConstant = getNextEnumConstant(nextEnumConstants, missingEnumElements);
    PsiElement bodyElement = body.getFirstBodyElement();
    while (bodyElement != null) {
      while (nextEnumConstant != null && findEnumConstant(bodyElement) == nextEnumConstant) {
        addSwitchLabelStatementBefore(missingEnumElements.get(0), bodyElement);
        missingEnumElements.remove(0);
        if (missingEnumElements.isEmpty()) {
          break;
        }
        nextEnumConstant = getNextEnumConstant(nextEnumConstants, missingEnumElements);
      }
      if (isDefaultSwitchLabelStatement(bodyElement)) {
        for (PsiEnumConstant missingEnumElement : missingEnumElements) {
          addSwitchLabelStatementBefore(missingEnumElement, bodyElement);
        }
        missingEnumElements.clear();
        break;
      }
      bodyElement = bodyElement.getNextSibling();
    }
    if (!missingEnumElements.isEmpty()) {
      final PsiElement lastChild = body.getLastChild();
      for (PsiEnumConstant missingEnumElement : missingEnumElements) {
        addSwitchLabelStatementBefore(missingEnumElement, lastChild);
      }
    }
  }

  private static void addSwitchLabelStatementBefore(PsiEnumConstant missingEnumElement, PsiElement anchor) {
    if (anchor instanceof PsiSwitchLabelStatement) {
      PsiElement sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      while (sibling instanceof PsiSwitchLabelStatement) {
        anchor = sibling;
        sibling = PsiTreeUtil.skipWhitespacesBackward(anchor);
      }
    }
    final PsiElement parent = anchor.getParent();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    final PsiStatement caseStatement = factory.createStatementFromText("case " + missingEnumElement.getName() + ":", anchor);
    parent.addBefore(caseStatement, anchor);
    final PsiStatement breakStatement = factory.createStatementFromText("break;", anchor);
    parent.addBefore(breakStatement, anchor);
  }

  private static PsiEnumConstant findEnumConstant(PsiElement element) {
    if (!(element instanceof PsiSwitchLabelStatement)) {
      return null;
    }
    final PsiSwitchLabelStatement switchLabelStatement = (PsiSwitchLabelStatement)element;
    final PsiExpression value = switchLabelStatement.getCaseValue();
    if (!(value instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)value;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiEnumConstant)) {
      return null;
    }
    return (PsiEnumConstant)target;
  }

  private static PsiEnumConstant getNextEnumConstant(Map<PsiEnumConstant, PsiEnumConstant> nextEnumConstants,
                                                     List<PsiEnumConstant> missingEnumElements) {
    PsiEnumConstant nextEnumConstant = nextEnumConstants.get(missingEnumElements.get(0));
    while (missingEnumElements.contains(nextEnumConstant)) {
      nextEnumConstant = nextEnumConstants.get(nextEnumConstant);
    }
    return nextEnumConstant;
  }

  private static boolean isDefaultSwitchLabelStatement(PsiElement element) {
    if (!(element instanceof PsiSwitchLabelStatement)) {
      return false;
    }
    final PsiSwitchLabelStatement switchLabelStatement = (PsiSwitchLabelStatement)element;
    return switchLabelStatement.isDefaultCase();
  }
}