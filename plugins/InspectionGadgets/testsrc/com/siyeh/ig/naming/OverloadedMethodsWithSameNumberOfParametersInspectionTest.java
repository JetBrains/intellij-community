/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ig.naming;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGInspectionTestCase;

public class OverloadedMethodsWithSameNumberOfParametersInspectionTest
  extends IGInspectionTestCase{

  public void testIgnoreInconvertibleTypes() throws Exception {
    doTest(new OverloadedMethodsWithSameNumberOfParametersInspection());
  }

  public void testReportAll() throws Exception {
    final OverloadedMethodsWithSameNumberOfParametersInspection inspection =
      new OverloadedMethodsWithSameNumberOfParametersInspection();
    inspection.ignoreInconvertibleTypes = false;
    doTest(inspection);
  }

  private void doTest(BaseInspection inspection) throws Exception {
    doTest("com/siyeh/igtest/naming/overloaded_methods_with_same_number_of_parameters/" +
           getTestName(true), inspection);
  }
}