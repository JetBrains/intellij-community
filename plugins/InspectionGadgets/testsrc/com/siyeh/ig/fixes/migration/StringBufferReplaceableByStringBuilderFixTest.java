/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.siyeh.ig.migration.StringBufferReplaceableByStringBuilderInspection;

public class StringBufferReplaceableByStringBuilderFixTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StringBufferReplaceableByStringBuilderInspection());
    myRelativePath = "stringBuffer_stringBuilder";
    myDefaultHint = InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.builder.replace.quickfix");
  }

  public void testCallChain() {
    doTest();
  }
}
