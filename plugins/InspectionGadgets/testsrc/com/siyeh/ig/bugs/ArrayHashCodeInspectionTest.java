// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class ArrayHashCodeInspectionTest extends LightJavaInspectionTestCase {

  public void testArrayHashCode() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ArrayHashCodeInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package java.util;" +
      "public class Objects {" +
      "    public static int hash(Object... values) {" +
      "        return Arrays.hashCode(values);" +
      "    }" +
      "}"
    };
  }
}
