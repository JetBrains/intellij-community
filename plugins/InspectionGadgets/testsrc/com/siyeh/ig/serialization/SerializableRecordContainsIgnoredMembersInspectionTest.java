// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    return JAVA_14;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.io;\n" +
      "import java.lang.annotation.*;\n" +
      "@Target({ElementType.METHOD, ElementType.FIELD})\n" +
      "@Retention(RetentionPolicy.SOURCE)\n" +
      "public @interface Serial {}"
    };
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SerializableRecordContainsIgnoredMembersInspection();
  }
}
