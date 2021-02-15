// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SerialVersionUIDNotStaticFinalInspectionTest extends LightJavaInspectionTestCase {

  public void testClassRightReturnType() {
    doTest("import java.io.*;\n" +
           "class C implements Serializable {\n" +
           "  static final long <warning descr=\"'serialVersionUID' field of a Serializable class is not declared 'private static final long'\"><caret>serialVersionUID</warning> = 1;\n" +
           "}");
    checkQuickFix("Make serialVersionUID 'private static final'",
                  "import java.io.*;\n" +
                  "class C implements Serializable {\n" +
                  "  private static final long serialVersionUID = 1;\n" +
                  "}");
  }

  public void testRecordComponent() {
    doTest("import java.io.*;\n" +
           "record R(long <warning descr=\"'serialVersionUID' field of a Serializable class is not declared 'private static final long'\"><caret>serialVersionUID</warning>) implements Serializable {\n" +
           "}");
    assertQuickFixNotAvailable("Make serialVersionUID 'private static final'");
  }

  public void testRecordStaticField() {
    doTest("import java.io.*;\n" +
           "record R() implements Serializable {\n" +
           "  static long <warning descr=\"'serialVersionUID' field of a Serializable class is not declared 'private static final long'\"><caret>serialVersionUID</warning> = 1;\n" +
           "}");
    checkQuickFix("Make serialVersionUID 'private static final'",
                  "import java.io.*;\n" +
                  "record R() implements Serializable {\n" +
                  "  private static final long serialVersionUID = 1;\n" +
                  "}");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SerialVersionUIDNotStaticFinalInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }
}