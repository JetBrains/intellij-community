// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class SerialVersionUIDNotStaticFinalInspectionTest extends LightJavaInspectionTestCase {

  public void testClassRightReturnType() {
    doTest("""
             import java.io.*;
             class C implements Serializable {
               static final long <warning descr="'serialVersionUID' field of a Serializable class is not declared 'private static final long'"><caret>serialVersionUID</warning> = 1;
             }""");
    checkQuickFix("Make serialVersionUID 'private static final'",
                  """
                    import java.io.*;
                    class C implements Serializable {
                      private static final long serialVersionUID = 1;
                    }""");
  }

  public void testRecordComponent() {
    doTest("""
             import java.io.*;
             record R(long <warning descr="'serialVersionUID' field of a Serializable class is not declared 'private static final long'"><caret>serialVersionUID</warning>) implements Serializable {
             }""");
    assertQuickFixNotAvailable("Make serialVersionUID 'private static final'");
  }

  public void testRecordStaticField() {
    doTest("""
             import java.io.*;
             record R() implements Serializable {
               static long <warning descr="'serialVersionUID' field of a Serializable class is not declared 'private static final long'"><caret>serialVersionUID</warning> = 1;
             }""");
    checkQuickFix("Make serialVersionUID 'private static final'",
                  """
                    import java.io.*;
                    record R() implements Serializable {
                      private static final long serialVersionUID = 1;
                    }""");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SerialVersionUIDNotStaticFinalInspection();
  }

}