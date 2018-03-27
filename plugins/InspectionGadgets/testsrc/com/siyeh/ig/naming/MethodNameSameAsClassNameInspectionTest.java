// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.naming;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

public class MethodNameSameAsClassNameInspectionTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDefaultHint = "Make method constructor";
  }

  @Override
  protected BaseInspection getInspection() {
    return new MethodNameSameAsClassNameInspection();
  }

  public void testSimple() { doTest(); }
  public void testModifiers() { doTest(); }
  public void testAbstract() { assertQuickfixNotAvailable(); }
  public void testInterface() { assertQuickfixNotAvailable(); }
  public void testConflictingCtor() { assertQuickfixNotAvailable(); }
  public void testConflictingCtorErasure() { assertQuickfixNotAvailable(); }

  @Override
  protected String getRelativePath() {
    return "naming/method_name_same_as_class_name";
  }
}