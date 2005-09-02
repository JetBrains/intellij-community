/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import sun.misc.DoubleConsts;
import sun.misc.FloatConsts;
import sun.misc.FpUtils;

import java.math.BigInteger;

public class ConvertIntegerToHexIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConvertIntegerToHexPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiLiteralExpression exp = (PsiLiteralExpression)element;
    final PsiType type = exp.getType();
    if (type.equals(PsiType.INT) || type.equals(PsiType.LONG)) {
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
      }
      else {
        val = new BigInteger(textString, 10);
      }
      @NonNls String hexString = "0x" + val.toString(16);
      if (isLong) {
        hexString += 'L';
      }
      replaceExpression(hexString, exp);
    }
    else {
      String textString = exp.getText();
      final int textLength = textString.length();
      final char lastChar = textString.charAt(textLength - 1);
      final boolean isFloat = lastChar == 'f' || lastChar == 'F';
      if (isFloat) {
        textString = textString.substring(0, textLength - 1);
      }

      if (isFloat) {
        final float floatValue = Float.parseFloat(textString);
        final String floatString = floatToHexString(floatValue) + lastChar;
        replaceExpression(floatString, exp);
      }
      else {
        final double doubleValue = Double.parseDouble(textString);
        final String floatString = doubleToHexString(doubleValue);
        replaceExpression(floatString, exp);
      }

    }
  }

  /**
   * Implementation copied from Double.toHexString(). This method is marked as @since 1.5 so we can't use that directly.
   * This is safe to delete when IDEA ships as running under 1.5 only.
   */
  private String doubleToHexString(double d) {
    /*
    * Modeled after the "a" conversion specifier in C99, section
    * 7.19.6.1; however, the output of this method is more
    * tightly specified.
    */
    if (!FpUtils.isFinite(d))
      // For infinity and NaN, use the decimal output.
      return Double.toString(d);
    else {
      // Initialized to maximum size of output.
      @NonNls StringBuffer answer = new StringBuffer(24);

      if (FpUtils.rawCopySign(1.0, d) == -1.0) // value is negative,
        answer.append("-");                     // so append sign info

      answer.append("0x");

      d = Math.abs(d);

      if (d == 0.0) {
        answer.append("0.0p0");
      }
      else {
        boolean subnormal = (d < DoubleConsts.MIN_NORMAL);

        // Isolate significand bits and OR in a high-order bit
        // so that the string representation has a known
        // length.
        long signifBits = (Double.doubleToLongBits(d)
                           & DoubleConsts.SIGNIF_BIT_MASK) |
                                                           0x1000000000000000L;

        // Subnormal values have a 0 implicit bit; normal
        // values have a a 1 implicit bit.
        answer.append(subnormal ? "0." : "1.");

        // Isolate the low-order 13 digits of the hex
        // representation.  If all the digits are zero,
        // replace with a single 0; otherwise, remove all
        // trailing zeros.
        String signif = Long.toHexString(signifBits).substring(3, 16);
        answer.append(signif.equals("0000000000000") ? // 13 zeros
                      "0" :
                      signif.replaceFirst("0{1,12}$", ""));

        // If the value is subnormal, use the E_min exponent
        // value for double; otherwise, extract and report d's
        // exponent (the representation of a subnormal uses
        // E_min -1).
        answer.append("p" + (subnormal ?
                             DoubleConsts.MIN_EXPONENT :
                             FpUtils.getExponent(d)));
      }
      return answer.toString();
    }
  }

  /**
   * Implementation copied from Double.toHexString(). This method is marked as @since 1.5 so we can't use that directly.
   * This is safe to delete when IDEA ships as running under 1.5 only.
   */
  private String floatToHexString(final float f) {
    if (Math.abs(f) < FloatConsts.MIN_NORMAL
        && f != 0.0f) {// float subnormal
      // Adjust exponent to create subnormal double, then
      // replace subnormal double exponent with subnormal float
      // exponent
      @NonNls String s = doubleToHexString(FpUtils.scalb((double)f,
                                                         /* -1022+126 */
                                                         DoubleConsts.MIN_EXPONENT -
                                                         FloatConsts.MIN_EXPONENT));
      return s.replaceFirst("p-1022$", "p-126");
    }
    else // double string will be the same as float string
      return doubleToHexString(f);
  }
}
