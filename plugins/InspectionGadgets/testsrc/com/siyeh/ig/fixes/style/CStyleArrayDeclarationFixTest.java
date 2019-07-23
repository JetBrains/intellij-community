// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.CStyleArrayDeclarationInspection;

/**
 * @author Bas Leijdekkers
 */
public class CStyleArrayDeclarationFixTest extends IGQuickFixesTestCase {

  public void testSimpleMethod() { doTest(); }
  public void testFieldWithWhitespace() { doTest(); }
  public void testInForLoop() { doTest(); }
  public void testMultipleVariablesSingleDeclaration() { doTest(); }
  public void testMultipleFieldsSingleDeclaration() { doTest(); }
  public void testParameter() { doTest(); }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CStyleArrayDeclarationInspection());
    myRelativePath = "style/cstyle_array_declaration";
    myDefaultHint = InspectionGadgetsBundle.message("c.style.array.declaration.replace.quickfix");
  }
}
