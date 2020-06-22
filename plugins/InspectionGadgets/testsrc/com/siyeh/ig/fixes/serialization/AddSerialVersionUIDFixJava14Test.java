// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.serialization;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.serialization.SerializableHasSerialVersionUIDFieldInspection;

public class AddSerialVersionUIDFixJava14Test extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SerializableHasSerialVersionUIDFieldInspection());
    myRelativePath = "serialization/serialVersionUID";
    myDefaultHint = InspectionGadgetsBundle.message("add.serialversionuidfield.quickfix");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) {
    builder.setLanguageLevel(LanguageLevel.JDK_14);
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

  public void testSerialAnnotation() {
    doTest();
  }
}
