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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class ErrorRethrownInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (Error /*Error 'e' not rethrown*/e/**/) {" +
                    "  e.printStackTrace();" +
                    "}");
  }

  public void testNoWarn1() {
    doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (Error e) {" +
                    "  e.printStackTrace();" +
                    "  throw e;" +
                    "}");
  }

  public void testNoWarn2() {
    doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (AssertionError e) {" +
                    "  e.printStackTrace();" +
                    "  throw e;" +
                    "}");
  }

  public void testSubclass() {
    doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (AssertionError /*Error 'e' not rethrown*/e/**/) {" +
                    "  e.printStackTrace();" +
                    "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ErrorRethrownInspection();
  }
}
