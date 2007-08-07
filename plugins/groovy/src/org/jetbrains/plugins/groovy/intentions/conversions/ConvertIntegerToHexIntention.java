package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.math.BigInteger;

public class ConvertIntegerToHexIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConvertIntegerToHexPredicate();
  }

  public void processIntention(PsiElement element)
      throws IncorrectOperationException {
    final GrLiteral exp = (GrLiteral) element;
    String textString = exp.getText();
    final int textLength = textString.length();
    final char lastChar = textString.charAt(textLength - 1);
    final boolean isLong = lastChar == 'l' || lastChar == 'L';
    if (isLong) {
      textString = textString.substring(0, textLength - 1);
    }

    final BigInteger val;
    if (textString.charAt(0) == '0') {
      val = new BigInteger(textString, 8);
    } else {
      val = new BigInteger(textString, 10);
    }
    @NonNls String hexString = "0x" + val.toString(16);
    if (isLong) {
      hexString += 'L';
    }
    replaceExpression(hexString, exp);
  }

}
