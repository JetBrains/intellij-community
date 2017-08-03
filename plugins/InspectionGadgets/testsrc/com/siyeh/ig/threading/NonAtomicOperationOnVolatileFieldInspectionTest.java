/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
           "    /*Non-atomic operation on volatile field 'v'*/v/**/++;" +
           "  }" +
           "}");
  }

  public void testPrefix() {
    doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    ++/*Non-atomic operation on volatile field 'v'*/v/**/;" +
           "  }" +
           "}");
  }

  public void testSimple() {
    doTest("class X {" +
           "  private volatile int v = 1;" +
           "  void m() {" +
           "    /*Non-atomic operation on volatile field 'v'*/v/**/ = 3 + v;" +
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

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new NonAtomicOperationOnVolatileFieldInspection();
  }
}