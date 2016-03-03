/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.PsiType;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToPlainIntention extends ConvertNumberIntentionBase {
  @Override
  protected String convertValue(final Number value, final PsiType type, final boolean negated) {
    String text = new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    if (negated) text = "-" + text;
    if (PsiType.FLOAT.equals(type)) text += "f";
    return text;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ConvertToPlainPredicate();
  }
}
