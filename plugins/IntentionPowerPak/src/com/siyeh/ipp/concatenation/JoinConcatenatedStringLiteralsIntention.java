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
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JoinConcatenatedStringLiteralsIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new StringConcatPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }
    if (!(element instanceof PsiJavaToken)) {
      return;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element.getParent();
    final StringBuilder newExpression = new StringBuilder();
    final PsiElement[] children = polyadicExpression.getChildren();
    final List<PsiElement> buffer = new SmartList<>();
    for (PsiElement child : children) {
      if (child instanceof PsiJavaToken) {
        if (token.equals(child)) {
          final PsiLiteralExpression literalExpression = (PsiLiteralExpression)buffer.get(0);
          final Object value = literalExpression.getValue();
          assert value != null;
          newExpression.append('"').append(StringUtil.escapeStringCharacters(value.toString()));
        }
        else {
          for (PsiElement bufferedElement : buffer) {
            newExpression.append(bufferedElement.getText());
          }
          buffer.clear();
          newExpression.append(child.getText());
        }
      }
      else if (child instanceof PsiLiteralExpression) {
        if (buffer.isEmpty()) {
          buffer.add(child);
        }
        else {
          final PsiLiteralExpression literalExpression = (PsiLiteralExpression)child;
          final Object value = literalExpression.getValue();
          assert value != null;
          newExpression.append(StringUtil.escapeStringCharacters(value.toString())).append('"');
          buffer.clear();
        }
      }
      else {
        if (buffer.isEmpty()) {
          newExpression.append(child.getText());
        }
        else {
          buffer.add(child);
        }
      }
    }
    for (PsiElement bufferedElement : buffer) {
      newExpression.append(bufferedElement.getText());
    }
    PsiReplacementUtil.replaceExpression(polyadicExpression, newExpression.toString());
  }
}
