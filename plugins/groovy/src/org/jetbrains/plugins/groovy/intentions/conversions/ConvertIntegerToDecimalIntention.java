// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.math.BigInteger;

public final class ConvertIntegerToDecimalIntention extends GrPsiUpdateIntention {

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ConvertIntegerToDecimalPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrLiteral exp = (GrLiteral)element;
    @NonNls String textString = exp.getText().replaceAll("_", "");
    final int textLength = textString.length();
    final char lastChar = textString.charAt(textLength - 1);
    final boolean isLong = lastChar == 'l' || lastChar == 'L';
    if (isLong) {
      textString = textString.substring(0, textLength - 1);
    }
    final BigInteger val;
    if (textString.startsWith("0x") || textString.startsWith("0X")) {
      final String rawIntString = textString.substring(2);
      val = new BigInteger(rawIntString, 16);
    }
    else if (textString.startsWith("0b") || textString.startsWith("0B")) {
      final String rawString = textString.substring(2);
      val = new BigInteger(rawString, 2);
    }
    else {
      final String rawIntString = textString.substring(1);
      val = new BigInteger(rawIntString, 8);
    }
    String decimalString = val.toString(10);
    if (isLong) {
      decimalString += 'L';
    }
    PsiImplUtil.replaceExpression(decimalString, exp);
  }
}
