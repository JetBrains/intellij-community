// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.cloneable;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.cloneable.CloneReturnsClassTypeInspection;

/**
 * @author Bas Leijdekkers
 */
public class CloneReturnsClassTypeFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CloneReturnsClassTypeInspection ());
    myRelativePath = "cloneable/change_return_type";
  }

  public void testSimple() { doTest("Change return type to 'Simple'"); }
  public void testReturnNull() { doTest("Change return type to 'ReturnNull'"); }
  public void testTypeUseAnnotation() { doTest("Change return type to 'TypeUseAnnotation'"); }
  public void testCast() { doTest("Change return type to 'Cast'"); }
  public void testAnonymousClass() { doTest("Change return type to 'B'"); }
  public void testNested() { doTest("Change return type to 'Nested'"); }

}
