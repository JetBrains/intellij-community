// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.resources;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import com.siyeh.ig.resources.IOResourceInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey
 */
public class IOResourceInspectionTest extends LightInspectionTestCase {

  public void testIOResource() {
    doTest();
  }

  public void testInsideTry() {
    final IOResourceInspection inspection = new IOResourceInspection();
    inspection.insideTryAllowed = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  public void testNoEscapingThroughMethodCalls() {
    final IOResourceInspection inspection = new IOResourceInspection();
    inspection.anyMethodMayClose = false;
    myFixture.enableInspections(inspection);
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new IOResourceInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.apache.commons.io;" +
      "import java.io.InputStream;" +
      "public class IOUtils {" +
      "  public static void closeQuietly(InputStream input) {}" +
      "}"
    };
  }
}
