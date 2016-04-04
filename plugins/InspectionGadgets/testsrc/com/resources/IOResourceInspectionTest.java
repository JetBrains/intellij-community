/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.resources;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import com.siyeh.ig.resources.IOResourceInspection;
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
