// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public abstract class SerializableCtorInspectionTestBase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(new SerializableCtorInspection());
  }

  protected void addPropertyMappingClass() {
    myFixture.addClass("package com.intellij.serialization;\n" +
                       "public @interface PropertyMapping { String[] value(); }");
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' +  getFileExtension());
  }

  @NotNull
  protected abstract String getFileExtension();
}
