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

public final class ConvertIntegerToHexIntention extends GrPsiUpdateIntention {


  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ConvertIntegerToHexPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrLiteral exp = (GrLiteral)element;
    String textString = exp.getText().replaceAll("_", "");
    final int textLength = textString.length();
    final char lastChar = textString.charAt(textLength - 1);
    final boolean isLong = lastChar == 'l' || lastChar == 'L';
    if (isLong) {
      textString = textString.substring(0, textLength - 1);
    }

    final BigInteger val;
    if (textString.startsWith("0b") || textString.startsWith("0B")) {
      val = new BigInteger(textString.substring(2), 2);
    }
    else if (textString.charAt(0) == '0') {
      val = new BigInteger(textString, 8);
    }
    else {
      val = new BigInteger(textString, 10);
    }
    @NonNls String hexString = "0x" + val.toString(16);
    if (isLong) {
      hexString += 'L';
    }
    PsiImplUtil.replaceExpression(hexString, exp);
  }
}
