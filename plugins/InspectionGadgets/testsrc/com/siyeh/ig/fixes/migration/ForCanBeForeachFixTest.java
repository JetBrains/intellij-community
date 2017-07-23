/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.migration;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.ForCanBeForeachInspection;

public class ForCanBeForeachFixTest extends IGQuickFixesTestCase {

  public void testParenthesis() { doTest(); }
  public void testInstanceofAndWhitespace() { doTest(); }
  public void testQualifyWithThis1() { doTest(); }
  public void testQualifyWithThis2() { doTest(); }
  public void testQualifyWithThisInner() { doTest(); }
  public void testNoQualifier() { doTest(); }
  public void testForThisClass() { doTest(); }
  public void testForOuterClass() { doTest(); }
  public void testForOuterClassIterator() { doTest(); }
  public void testForQualifiedArray() { doTest(); }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ForCanBeForeachInspection());
    myRelativePath = "migration/for_can_be_foreach";
    myDefaultHint = InspectionGadgetsBundle.message("foreach.replace.quickfix");
  }
}
