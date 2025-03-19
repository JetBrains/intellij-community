// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

public abstract class GrFunctionalExpressionCompletionTest extends GroovyCompletionTestBase {
  public void testInjectMethodForCollection() { doBasicTest(); }

  public void testEachMethodForMapWithKeyValue() { doBasicTest(); }

  public void testEachMethodForList() { doBasicTest(); }

  public void testClosureDefaultParameterInEachMethod() { doBasicTest(); }

  public void testEachMethodForEnumRanges() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.completeBasic();
    myFixture.type("\n");
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testEachMethodForMapWithEntry() { doBasicTest(); }

  public void testEachMethodForRanges() { doBasicTest(); }

  public void testInjectMethodForArray() { doBasicTest(); }

  public void testCompletionInEachClosure() {
    doHasVariantsTest("intValue", "intdiv");
  }

  public void testWithMethod() { doBasicTest(); }

  public void testInjectMethodForMap() { doBasicTest(); }
}
