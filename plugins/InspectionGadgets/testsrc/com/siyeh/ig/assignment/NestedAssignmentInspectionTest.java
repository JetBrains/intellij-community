// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class NestedAssignmentInspectionTest extends LightInspectionTestCase {

  public void testLambda() {
    doTest("class Test {" +
           " {" +
           "    int[] array = new int[1];" +
           "    Runnable r = () -> array[0] = 0;" +
           " }" +
           "}");
  }

  public void testSwitchExpression() {
    doTest("class Main {\n" +
           "    int test(int i) {\n" +
           "        int j = switch(i) {\n" +
           "            default -> /*Result of assignment expression used*/i = 2/**/;\n" +
           "        };\n" +
           "        return j+i;\n" +
           "    }\n" +
           "}");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new NestedAssignmentInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }
}