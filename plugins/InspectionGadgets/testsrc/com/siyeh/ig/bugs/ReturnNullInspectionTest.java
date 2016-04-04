/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ReturnNullInspectionTest extends LightInspectionTestCase {

  public void testReturnNull() {
    doTest();
  }

  public void testWarnOptional() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ReturnNullInspection inspection = new ReturnNullInspection();
    inspection.m_reportObjectMethods = !"WarnOptional".equals(getTestName(false));
    inspection.m_ignorePrivateMethods = "WarnOptional".equals(getTestName(false));
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public final class Optional<T> {}",

      "package java.util;" +
      "public final class OptionalInt {}"
    };
  }
}