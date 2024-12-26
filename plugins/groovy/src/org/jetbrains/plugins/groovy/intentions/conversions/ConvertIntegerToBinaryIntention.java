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

/**
 * @author Max Medvedev
 */
public final class ConvertIntegerToBinaryIntention extends GrPsiUpdateIntention {
  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ConvertIntegerToBinaryPredicate();
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
      final String rawTextString = textString.substring(2);
      val = new BigInteger(rawTextString, 16);
    }
    else if (textString.startsWith("0")) {
      final String rawTextString = textString.substring(2);
      val = new BigInteger(rawTextString, 8);
    }
    else {
      val = new BigInteger(textString, 10);
    }
    String octString = "0b" + val.toString(2);
    if (isLong) {
      octString += 'L';
    }
    PsiImplUtil.replaceExpression(octString, exp);
  }
}

