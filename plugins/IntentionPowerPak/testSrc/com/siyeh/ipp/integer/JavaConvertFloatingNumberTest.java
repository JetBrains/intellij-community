// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.integer;

import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.ipp.IPPTestCase;

import java.util.regex.Pattern;

public class JavaConvertFloatingNumberTest extends IPPTestCase {
  public void testToPlain() { doTestWithChooser("Plain format"); }
  public void testToPlainShort() { doTestWithChooser("Plain format"); }
  public void testToPlainLong() { doTestWithChooser("Plain format"); }
  public void testToHex() { doTestWithChooser("Hex"); }
  public void testNegatedFloatToPlain() { doTestWithChooser("Plain format"); }
  public void testWithUnderscoresToPlain() { doTestWithChooser("Plain format"); }
  public void testNoTrailingZeros() { doTestWithChooser("Plain format"); }
  public void testToSci() { doTestWithChooser("Scientific format"); }
  public void testNegatedFloatToSci() { doTestWithChooser("Scientific format"); }
  public void testWithUnderscoresToSci() { doTestWithChooser("Scientific format"); }

  private void doTestWithChooser(String selectedOption) {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote(selectedOption) + " \\(.*\\)"));
    doTest();
  }
  
  @Override
  protected String getIntentionName() {
    return "Convert number to…";
  }

  @Override
  protected String getRelativePath() {
    return "float";
  }
}
