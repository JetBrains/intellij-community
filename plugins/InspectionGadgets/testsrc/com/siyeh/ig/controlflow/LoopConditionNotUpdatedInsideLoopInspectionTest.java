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
public class LoopConditionNotUpdatedInsideLoopInspectionTest extends LightInspectionTestCase {

  public void testPolyadicExpression() {
    doMemberTest("void m(boolean b1, boolean b2, boolean b3) {" +
                 "    while(/*Variable 'b1' is not updated inside loop*/b1/**/ && " +
                 "          /*Variable 'b2' is not updated inside loop*/b2/**/ && " +
                 "          /*Variable 'b3' is not updated inside loop*/b3/**/) {}" +
                 "}");
  }

  public void testFinalLocal() {
    doMemberTest("void foo(java.io.InputStream in) throws java.io.IOException {" +
                 "    final int i = in.read();" +
                 "    while (/*Condition 'i != -1' is not updated inside loop*/i != -1/**/) {}" +
                 "}");
  }

  public void testNull() {
    doMemberTest("void arg(Object o) {\n" +
                 "    while (/*Variable 'o' is not updated inside loop*/o/**/ != null) {}" +
                 "}");
  }

  public void testIterator() {
    doMemberTest("void iterate(java.util.Iterator iterator) {" +
                 "    while (/*Variable 'iterator' is not updated inside loop*/iterator/**/.hasNext()) {}" +
                 "}");
  }

  public void testArrayVariable1() {
    doMemberTest("void test() {" +
                 "        int[] sockets = new int[1];" +
                 "        while (sockets[0] == 0)" +
                 "            sockets = new int[1];" +
                 "    }");
  }

  public void testArrayVariable2() {
    doMemberTest("void test() {" +
                 "        int[] sockets = new int[1];" +
                 "        while (sockets[0] == 0)" +
                 "            sockets[0] = 1;" +
                 "    }");
  }

  public void testArrayVariable3() {
    doMemberTest("void test() {" +
                 "        int[] sockets = new int[1];" +
                 "        while (/*Variable 'sockets' is not updated inside loop*/sockets/**/[0] == 0) {}" +
                 "    }");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new LoopConditionNotUpdatedInsideLoopInspection();
  }
}
