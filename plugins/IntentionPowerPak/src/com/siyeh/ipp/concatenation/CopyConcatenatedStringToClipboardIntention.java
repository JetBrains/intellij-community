/*
 * Copyright 2008-2018 Bas Leijdekkers
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

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class CopyConcatenatedStringToClipboardIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new SimpleStringConcatenationPredicate(false);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return;
    }
    final PsiPolyadicExpression concatenationExpression = (PsiPolyadicExpression)element;
    final IElementType tokenType = concatenationExpression.getOperationTokenType();
    if (tokenType != JavaTokenType.PLUS) {
      return;
    }
    final PsiType type = concatenationExpression.getType();
    if (type == null || !type.equalsToText(JAVA_LANG_STRING)) {
      return;
    }
    final StringBuilder text = buildConcatenationText(concatenationExpression, new StringBuilder());
    CopyPasteManager.getInstance().setContents(new StringSelection(text.toString()));
  }

  private static StringBuilder buildConcatenationText(PsiPolyadicExpression polyadicExpression, StringBuilder out) {
    for (PsiElement element : polyadicExpression.getChildren()) {
      if (element instanceof PsiExpression) {
        final PsiExpression expression = (PsiExpression)element;
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        if (value == null) {
          out.append('?');
        }
        else {
          out.append(value.toString());
        }
      }
      else if (element instanceof PsiWhiteSpace && element.getText().contains("\n") &&
               (out.length() == 0 || out.charAt(out.length() - 1) != '\n')) {
        out.append('\n');
      }
    }
    return out;
  }
}
