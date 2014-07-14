/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Max Medvedev
 */
public class ConvertIntegerToBinaryPredicate implements PsiElementPredicate {
  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrLiteral)) return false;

    final GrLiteral expression = (GrLiteral)element;
    final PsiType type = expression.getType();
    if (type == null) return false;

    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type) &&
        !type.equalsToText("java.lang.Integer") && !type.equalsToText("java.lang.Long")) {
      return false;
    }
    @NonNls final String text = expression.getText();
    if (text == null || text.isEmpty()) {
      return false;
    }
    if (text.startsWith("0x") || text.startsWith("0X")) {
      return true;
    }
    if (text.startsWith("0b") || text.startsWith("0B")) {
      return false;
    }
    if ("0".equals(text) || "0L".equals(text)) {
      return true;
    }
    return text.charAt(0) != '0';
  }
}

