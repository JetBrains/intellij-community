// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SerializableRecordContainsIgnoredMembersInspectionTest extends LightJavaInspectionTestCase {

  public void testSerializableRecord() {
    doTest();
  }

  public void testExternalizableRecord() {
    doTest();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package java.io;
import java.lang.annotation.*;
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Serial {}"""
    };
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SerializableRecordContainsIgnoredMembersInspection();
  }
}
