package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.math.BigInteger;

public class ConvertIntegerToDecimalIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConvertIntegerToDecimalPredicate();
  }

  public void processIntention(PsiElement element)
      throws IncorrectOperationException {
    final GrLiteral exp = (GrLiteral) element;
    @NonNls String textString = exp.getText();
    final int textLength = textString.length();
    final char lastChar = textString.charAt(textLength - 1);
    final boolean isLong = lastChar == 'l' || lastChar == 'L';
    if (isLong) {
      textString = textString.substring(0, textLength - 1);
    }
    final BigInteger val;
    if (textString.startsWith("0x")) {
      final String rawIntString = textString.substring(2);
      val = new BigInteger(rawIntString, 16);
    } else {
      final String rawIntString = textString.substring(1);
      val = new BigInteger(rawIntString, 8);
    }
    String decimalString = val.toString(10);
    if (isLong) {
      decimalString += 'L';
    }
    replaceExpression(decimalString, exp);
  }
}
