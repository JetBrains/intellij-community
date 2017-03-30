/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.vcs;

import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

@NonNls public class ErrorMessageTest extends TestCase {
  public void test() throws Exception{

    doTest(1, 1, "One error and one warning found.");
    doTest(0, 1, "No errors and one warning found.");
    doTest(1, 0, "One error and no warnings found.");
    doTest(1, 10, "One error and 10 warnings found.");
    doTest(10, 1, "10 errors and one warning found.");
    doTest(10, 0, "10 errors and no warnings found.");
    doTest(0, 10, "No errors and 10 warnings found.");
  }

  private void doTest(final int errors, final int warnings, final String expected) {
    final String message = VcsBundle.message("before.commit.files.contain.code.smells.edit.them.confirm.text", errors, warnings);
    assertTrue(message.indexOf(expected) >= 0);
  }
}
