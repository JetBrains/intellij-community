// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EqualsWhichDoesntCheckParameterClassInspectionTest extends LightJavaInspectionTestCase {

  public void testEqualsWhichDoesntCheckParameterClass() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new EqualsWhichDoesntCheckParameterClassInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.apache.commons.lang3.builder;" +
      "public class EqualsBuilder {" +
      "  public static boolean reflectionEquals(Object lhs, Object rhs, String... excludeFields) {" +
      "    return true;" +
      "  }" +
      "}"
    };
  }
}
