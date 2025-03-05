// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

class ConvertIntegerToOctalPredicate implements PsiElementPredicate {
  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof GrLiteral expression)) return false;

    final PsiType type = expression.getType();
    if (type == null) return false;

    if (!PsiTypes.intType().equals(type) && !PsiTypes.longType().equals(type) &&
        !type.equalsToText("java.lang.Integer") && !type.equalsToText("java.lang.Long")) {
      return false;
    }
    final @NonNls String text = expression.getText();
    if (text == null || text.isEmpty()) {
      return false;
    }
    if (text.startsWith("0x") || text.startsWith("0X")) {
      return true;
    }
    if (text.startsWith("0b") || text.startsWith("0B")) {
      return true;
    }
    if ("0".equals(text) || "0L".equals(text)) {
      return false;
    }
    return text.charAt(0) != '0';
  }
}
