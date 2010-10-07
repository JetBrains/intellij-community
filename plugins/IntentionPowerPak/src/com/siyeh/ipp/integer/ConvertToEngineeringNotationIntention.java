/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToEngineeringNotationIntention extends Intention {
  private static final DecimalFormat FORMAT = new DecimalFormat("0.00000000000000E00");
  private static final ConvertToEngineeringNotationPredicate PREDICATE = new ConvertToEngineeringNotationPredicate();

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    String text = FORMAT.format(Double.parseDouble(element.getText())).replace(',', '.');
    while (text.contains("0E") && !text.contains(".0E")) {
      text = text.replace("0E", "E");
    }
    replaceExpression(text, (PsiExpression)element);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return PREDICATE;
  }
}
