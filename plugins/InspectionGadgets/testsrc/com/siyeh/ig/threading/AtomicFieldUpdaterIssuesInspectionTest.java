/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
public class AtomicFieldUpdaterIssuesInspectionTest extends LightInspectionTestCase {

  public void testAllGood() {
    doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private volatile int value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, \"value\");" +
           "}");
  }

  public void testStatic() {
    doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private static volatile int value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater((A.class), /*Field 'value' has 'static' modifier*/(\"value\")/**/);" +
           "}");
  }

  public void testNotVolatile() {
    doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private int value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, /*Field 'value' does not have 'volatile' modifier*/\"value\"/**/);" +
           "}");
  }

  public void testFieldNotFound() {
    doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class Z {" +
           "  volatile int value = 0;" +
           "}" +
           "class A extends Z {" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, /*No field named 'value' found in class 'A'*/\"value\"/**/);" +
           "}");
  }

  public void testWrongType1() {
    doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private volatile long value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, /*Field 'value' does not have type 'int'*/\"value\"/**/);" +
           "}");
  }

  public void testWrongType2() {
    doTest("import java.util.concurrent.atomic.AtomicLongFieldUpdater;" +
           "class A {" +
           "  private volatile int value = 0;" +
           "  private static final AtomicLongFieldUpdater updater = " +
           "    AtomicLongFieldUpdater.newUpdater(A.class, /*Field 'value' does not have type 'long'*/\"value\"/**/);" +
           "}");
  }

  public void testWrongType3() {
    doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "class A {" +
           "  private volatile int[] value = new int[]{0};" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(A.class, long[].class, /*Field 'value' does not have type 'long[]'*/\"value\"/**/);" +
           "}");
  }

  public void testNotAccessible1() {
    doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class Z {" +
           "  private volatile int value = 0;" +
           "}" +
           "class A {" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(Z.class, /*'private' field 'value' is not accessible from here*/\"value\"/**/);" +
           "}");
  }

  /**
   * private fields are not accessible at runtime even from inner classes.
   */
  public void testNotAccessible2() {
    doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class Z {" +
           "  private volatile int value = 0;" +
           "  static class A {\n" +
           "    private static final AtomicIntegerFieldUpdater updater = \n" +
           "      AtomicIntegerFieldUpdater.newUpdater(Z.class, /*'private' field 'value' is not accessible from here*/\"value\"/**/);\n" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AtomicFieldUpdaterIssuesInspection();
  }
}