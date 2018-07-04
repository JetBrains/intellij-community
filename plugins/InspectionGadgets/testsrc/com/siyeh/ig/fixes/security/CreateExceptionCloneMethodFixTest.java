// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.security;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.security.CloneableClassInSecureContextInspection;

/**
 * @author Bas Leijdekkers
 */
public class CreateExceptionCloneMethodFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CloneableClassInSecureContextInspection());
    myRelativePath = "security/create_exception_clone_method";
    myDefaultHint = InspectionGadgetsBundle.message("cloneable.class.in.secure.context.quickfix");
  }

  public void testSimple() { doTest(); }
  public void testPublicNoThrows() { doTest(); }
  public void testGeneric() { doTest(); }

}
