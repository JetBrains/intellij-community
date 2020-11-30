// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.trivialif;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ExpandBooleanIntentionTest extends IPPTestCase {

  public void testBraces() { doFormattingTest(IntentionPowerPackBundle.message("expand.boolean.declaration.intention.name"));}
  public void testFormatting() { doFormattingTest(IntentionPowerPackBundle.message("expand.boolean.return.intention.name")); }
  public void testFormatting2() { doFormattingTest(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  public void testIncomplete1() { doTest(IntentionPowerPackBundle.message("expand.boolean.return.intention.name")); }
  public void testIncomplete2() { doTest(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  public void testIncomplete3() { assertIntentionNotAvailable(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  public void testIncomplete4() { assertIntentionNotAvailable(); }
  public void testIncomplete5() { assertIntentionNotAvailable(IntentionPowerPackBundle.message("expand.boolean.assignment.intention.name")); }
  public void testIncomplete6() { assertIntentionNotAvailable(IntentionPowerPackBundle.message("expand.boolean.return.intention.name")); }

  private void doFormattingTest(String intentionText) {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    final int oldValue = settings.IF_BRACE_FORCE;
    try {
      settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
      doTest(intentionText);
    } finally {
      settings.IF_BRACE_FORCE = oldValue;
    }
  }

  @Override
  protected String getRelativePath() {
    return "trivialif/expand_boolean";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("expand.boolean.declaration.intention.name");
  }
}
