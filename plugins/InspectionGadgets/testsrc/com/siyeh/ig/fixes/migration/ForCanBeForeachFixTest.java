// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.migration;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.ForCanBeForeachInspection;

public class ForCanBeForeachFixTest extends IGQuickFixesTestCase {

  public void testParenthesis() { doTest(); }
  public void testParenthesisArrayLoop() { doTest(); }
  public void testParenthesisArrayLoop2() { doTest(); }
  public void testParenthesisIteratorLoop() { doTest(); }
  public void testInstanceofAndWhitespace() { doTest(); }
  public void testQualifyWithThis1() { doTest(); }
  public void testQualifyWithThis2() { doTest(); }
  public void testQualifyWithThisInner() { doTest(); }
  public void testNoQualifier() { doTest(); }
  public void testForThisClass() { doTest(); }
  public void testForOuterClass() { doTest(); }
  public void testForOuterClassIterator() { doTest(); }
  public void testForQualifiedArray() { doTest(); }
  public void testForFieldName() {
    JavaCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    try {
      doTest();
    }
    finally {
      settings.FIELD_NAME_PREFIX = oldPrefix;
    }
  }
  public void testRawCollection() { doTest(); }
  public void testArrayUnboxing() { doTest(); }
  public void testListUnboxing() { doTest(); }
  public void testIteratorUnboxing() { doTest(); }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ForCanBeForeachInspection());
    myRelativePath = "migration/for_can_be_foreach";
    myDefaultHint = InspectionGadgetsBundle.message("foreach.replace.quickfix");
  }
}
