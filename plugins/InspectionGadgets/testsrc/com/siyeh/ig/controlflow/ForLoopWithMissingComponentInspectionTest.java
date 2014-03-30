/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ForLoopWithMissingComponentInspectionTest extends LightInspectionTestCase {

  public void testMissingAllComponents() {
    doStatementTest("/*'for' statement lacks initializer, condition and update*/for/**/ (;;) {}");
  }

  public void testMissingConditionAndUpdate() {
    doStatementTest("/*'for' statement lacks condition and update*/for/**/ (int i = 0;;);");
  }

  public void testMissingInitializationAndUpdate() {
    doStatementTest("/*'for' statement lacks initializer and update*/for/**/ (;true;);");
  }

  public void testMissingCondition() {
    doStatementTest("/*'for' statement lacks condition*/for/**/ (int i = 0;;i++);");
  }

  public void testCorrect() {
    doStatementTest("for(int i = 0;true;i++);");
  }

  public void testIterator() {
    doMemberTest("void m(java.util.List l) {" +
                 "  for (java.util.ListIterator it = l.listIterator(); it.hasNext();) {" +
                 "    System.out.println(it.next());" +
                 "  }" +
                 "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    final ForLoopWithMissingComponentInspection inspection = new ForLoopWithMissingComponentInspection();
    inspection.ignoreCollectionLoops = true;
    return inspection;
  }
}
