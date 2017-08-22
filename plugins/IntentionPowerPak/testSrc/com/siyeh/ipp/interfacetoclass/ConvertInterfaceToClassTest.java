// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE.txt file.
package com.siyeh.ipp.interfacetoclass;

import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class ConvertInterfaceToClassTest extends IPPTestCase {
  public void testBasic() { doTest(); }
  public void testExtensionMethods() { doTest(); }
  public void testInnerInterface() { doTest(); }
  public void testStaticMethods() { doTest(); }
  public void testInterfaceExtendsClass() { doTest(); }
  public void testFunctionalExpressions() {
    try {
      doTest();
      fail("Conflict not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("() -> {...} in Test will not compile after converting class <b><code>FunctionalExpressions</code></b> to a class", e.getMessage());
    }
  }

  public void testFunctionalInterface() {
    assertIntentionNotAvailable();
  }

  @Override
  protected String getRelativePath() {
    return "interfaceToClass";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.name");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
