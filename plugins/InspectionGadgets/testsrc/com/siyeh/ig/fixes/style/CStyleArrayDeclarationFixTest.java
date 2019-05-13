/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CStyleArrayDeclarationInspection());
    myRelativePath = "style/cstyle_array_declaration";
    myDefaultHint = InspectionGadgetsBundle.message("c.style.array.declaration.replace.quickfix");
  }
}
