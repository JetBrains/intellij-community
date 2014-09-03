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
package com.siyeh.ig.fixes.threading;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.threading.DoubleCheckedLockingInspection;

/**
 * @author Bas Leijdekkers
 */
public class MakeFieldVolatileFixTest extends IGQuickFixesTestCase {

  public void testSimple() { doTest(InspectionGadgetsBundle.message("double.checked.locking.quickfix", "s_instance")); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final DoubleCheckedLockingInspection inspection = new DoubleCheckedLockingInspection();
    inspection.ignoreOnVolatileVariables = true;
    myFixture.enableInspections(inspection);
    myRelativePath = "threading/make_field_volatile";
  }
}
