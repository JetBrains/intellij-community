/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.increment;

import com.intellij.psi.PsiElement;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.assignment.IncrementDecrementUsedAsExpressionInspection;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExtractIncrementIntention extends MutablyNamedIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("extract.increment.intention.family.name");
  }

  @Override
  public String getTextForElement(PsiElement element) {
    final String operatorText = IncrementUtil.getOperatorText(element);
    return operatorText != null ? IntentionPowerPackBundle.message(
      "extract.increment.intention.name", operatorText) : null;
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ExtractIncrementPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    IncrementDecrementUsedAsExpressionInspection.extractPrefixPostfixExpressionToSeparateStatement(element);
  }
}