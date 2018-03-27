// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public void testRightType() {
    doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import java.util.RandomAccess;" +
           "class A<T extends RandomAccess> {" +
           "  private volatile T value = null;" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(A.class, RandomAccess.class, \"value\");" +
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

  public void testNotAccessible3() {
    doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import one.ProtectedOther;" +
           "class X {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(ProtectedOther.class, String.class, /*'protected' field 's' is not accessible from here*/\"s\"/**/);" +
           "}");
  }

  public void testNotAccessible4() {
    doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import one.PackageLocalOther;" +
           "class X {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(PackageLocalOther.class, String.class, /*Package-private field 's' is not accessible from here*/\"s\"/**/);" +
           "}");
  }

  public void testAccessible1() {
    doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "class X {" +
           "  protected volatile String value;" +
           "  private static final AtomicReferenceFieldUpdater<X, String> X_UPDATER = AtomicReferenceFieldUpdater\n" +
           "          .newUpdater(X.class, String.class, \"value\");" +
           "}");
  }

  public void testAccessible2() {
    doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import one.ProtectedOther;" +
           "class X extends ProtectedOther {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(ProtectedOther.class, String.class, \"s\");" +
           "}");
  }

  public void testAccessible3() {
    doTest("package one;" +
           "import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "class X {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(PackageLocalOther.class, String.class, \"s\");" +
           "}");
  }

  public void testAvoidNPE() {
    doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;\n" +
           "class Z</*!Cyclic inheritance involving 'T'*//*!*/T extends T> {\n" +
           "  private T value = null;\n" +
           "  private static final AtomicReferenceFieldUpdater updater = \n" +
           "      AtomicReferenceFieldUpdater.newUpdater(Z.class, Object.class, \"value\");\n" +
           "}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package one;" +
      "public class ProtectedOther {" +
      "  protected volatile String s;" +
      "}"
      ,
      "package one;" +
      "public class PackageLocalOther {" +
      "  volatile String s;" +
      "}"
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AtomicFieldUpdaterIssuesInspection();
  }
}