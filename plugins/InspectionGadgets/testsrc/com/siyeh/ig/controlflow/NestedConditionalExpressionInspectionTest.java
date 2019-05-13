// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class NestedConditionalExpressionInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("class X {" +
           "   boolean x(int i, int j) {" +
           "     return i == 0 ? true : /*Nested conditional expression 'j == 0 ? true : false'*/j == 0 ? true : false/**/;" +
           "   }" +
           "}");
  }
  
  public void testLambda() {
    doTest("import java.util.function.IntFunction;" +
           "class X {" +
           "   private IntFunction<String> nullIfEmpty(String str) {\n" +
           "     return str == null ? null : (a) -> (str.isEmpty() ? null : str);\n" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new NestedConditionalExpressionInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}