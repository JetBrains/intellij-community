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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class CompareToUsesNonFinalVariableInspectionTest extends LightJavaInspectionTestCase {

  public void testForeignComparable() {
    doMemberTest("class A {" +
                 "  private int myF = 0;" +
                 "  public int compareTo(Object o){" +
                 "    return myF;" +
                 "  }" +
                 "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new CompareToUsesNonFinalVariableInspection();
  }
}
