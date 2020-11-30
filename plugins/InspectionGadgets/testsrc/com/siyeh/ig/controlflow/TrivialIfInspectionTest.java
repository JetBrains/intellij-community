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

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class TrivialIfInspectionTest extends LightJavaInspectionTestCase {

  public void testParenthesesReturn() {
    doMemberTest("boolean b(int[] array) {" +
                 "  /*'if' statement can be simplified*/if/**/ (array.length == 10) {" +
                 "    return (true);" +
                 "  } else{" +
                 "    return false;" +
                 "  }" +
                 "}");
  }

  public void testParenthesesReturnNestedIf() {
    doMemberTest("\n" +
                 "  boolean b(int[] array) {\n" +
                 "    if (array != null) {\n" +
                 "      int len = array.length;\n" +
                 "      /*'if' statement can be simplified*/if/**/(len == 10) return true;\n" +
                 "    }\n" +
                 "    return false;\n" +
                 "  }\n" +
                 "");
  }

  public void testParenthesesAssignment() {
    doMemberTest("void b(int[] array) {" +
                 "  boolean result;" +
                 "  /*'if' statement can be simplified*/if/**/ (array.length == 10) {" +
                 "    result = (true);" +
                 "  } else{" +
                 "    result = (((false)));" +
                 "  }" +
                 "}");
  }

  public void testReturn() {
    doMemberTest("\n" +
                 "  boolean b(int x) {\n" +
                 "    if (x > 20) return true;\n" +
                 "    /*'if' statement can be simplified*/if/**/ (x > 0) return true;\n" +
                 "    return false;\n" +
                 "}\n");
  }

  public void testReturnIgnoreChain() {
    doMemberTest("\n" +
                 "  boolean b(int x) {\n" +
                 "    if (x > 20) return true;\n" +
                 "    if (x > 0) return true;\n" +
                 "    return false;\n" +
                 "}\n");
  }

  public void testReturnElseIf() {
    doMemberTest("\n" +
                 "  boolean b(int x) {\n" +
                 "    if (x > 20) return true;\n" +
                 "    else /*'if' statement can be simplified*/if/**/ (x > 0) return true;\n" +
                 "    else return false;\n" +
                 "}\n");
  }

  public void testReturnElseIfIgnoreChain() {
    doMemberTest("\n" +
                 "  boolean b(int x) {\n" +
                 "    if (x > 20) return true;\n" +
                 "    else if (x > 0) return true;\n" +
                 "    else return false;\n" +
                 "}\n");
  }
  
  public void testReturnEqualBranches() {
    // no warning: another inspection takes care about this
    doMemberTest("\n" +
                 "  boolean b(int x) {\n" +
                 "    if (x > 20) return true;\n" +
                 "    else return true;\n" +
                 "}\n");
  }

  public void testMethodCall() {
    doMemberTest("void test(int x, Boolean foo) {\n" +
                 "  if (x == 0) System.out.println(foo);\n" +
                 "  else {\n" +
                 "    /*'if' statement can be simplified*/if/**/ (x > 0) test(0, true);\n" +
                 "    else test(0, false);\n" +
                 "  }\n" +
                 "}");
  }

  public void testOverwrittenDeclaration() {
    doMemberTest("boolean test(int x) {\n" +
                 "  boolean result = false;\n" +
                 "  /*'if' statement can be simplified*/if/**/ (x == 0) result = true;\n" +
                 "  return result;\n" +
                 "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    TrivialIfInspection inspection = new TrivialIfInspection();
    if (getTestName(false).endsWith("IgnoreChain")) {
      inspection.ignoreChainedIf = true;
    }
    return inspection;
  }
}
