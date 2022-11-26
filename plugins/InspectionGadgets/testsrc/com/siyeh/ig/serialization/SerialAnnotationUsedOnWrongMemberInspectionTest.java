// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SerialAnnotationUsedOnWrongMemberInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package java.io;
import java.lang.annotation.*;
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Serial  {}"""
    };
  }

  public void testSerializableClassPositive() {
    doTest();
  }

  public void testSerializableClassNegative() {
    doTest();
  }

  public void testExternalizableClassPositive() {
    doTest();
  }

  public void testExternalizableClassNegative() {
    doTest();
  }

  public void testSuppressedSerial() {
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SerialAnnotationUsedOnWrongMemberInspection();
  }
}
