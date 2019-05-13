// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("NonAtomicOperationOnVolatileField")
public class NonAtomicOperationOnVolatileFieldInspectionTest extends LightInspectionTestCase {

  public void testWriteToDifferentInstance() {
    doTest("class A {\n" +
           "    private volatile int v;\n" +
           "\n" +
           "    A copy() {\n" +
           "        A a = new A();\n" +
           "        a.v = v;\n" +
           "        return a;\n" +
           "    }\n" +
           "}");
  }

  public void testPostfix() {
    doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    (/*Non-atomic operation on volatile field 'v'*/v/**/)++;" +
           "  }" +
           "}");
  }

  public void testPrefix() {
    doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    ++(/*Non-atomic operation on volatile field 'v'*/v/**/);" +
           "  }" +
           "}");
  }

  public void testSimple() {
    doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    (/*Non-atomic operation on volatile field 'v'*/v/**/) = 3 + v;" +
           "  }" +
           "}");
  }

  public void testQualified() {
    doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    (this)./*Non-atomic operation on volatile field 'v'*/v/**/ = 2 * (this).v;" +
           "  }" +
           "}");
  }

  public void testQualified2() {
    doTest("class Segment {" +
           "  private volatile int count = 0;" +
           "  public void x() {" +
           "      Runnable r = new Runnable() {" +
           "        @Override" +
           "        public void run() {" +
           "          (Segment.this./*Non-atomic operation on volatile field 'count'*/count/**/)++;" +
           "        }" +
           "      };" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new NonAtomicOperationOnVolatileFieldInspection();
  }
}