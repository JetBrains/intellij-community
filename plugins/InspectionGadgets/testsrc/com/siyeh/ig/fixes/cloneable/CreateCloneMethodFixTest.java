// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.cloneable;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.cloneable.CloneableImplementsCloneInspection;

/**
 * @author Bas Leijdekkers
 */
public class CreateCloneMethodFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CloneableImplementsCloneInspection());
    myRelativePath = "cloneable/create_clone_method";
    myDefaultHint = InspectionGadgetsBundle.message("cloneable.class.without.clone.quickfix");
  }

  public void testFinal() { assertQuickfixNotAvailable();}
  public void testSimple() { doTest();}
  public void testNoThrows() { doTest();}
  public void testGenerics() { doTest();}

}
