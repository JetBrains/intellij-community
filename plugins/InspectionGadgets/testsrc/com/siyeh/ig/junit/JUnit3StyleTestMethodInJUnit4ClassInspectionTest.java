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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class JUnit3StyleTestMethodInJUnit4ClassInspectionTest extends LightJavaInspectionTestCase {

  public void testJUnit3StyleTestMethodInJUnit4Class() { doTest(); }
  public void testBeforeAnnotationUsed() { doTest(); }
  public void testSimpleJUnit5() { doTest(); }
  public void testOtherAnnotation() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new JUnit3StyleTestMethodInJUnit4ClassInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "import java.lang.annotation.*;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Before {}",
      "package org.junit;" +
      "import java.lang.annotation.*;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface After {}",
      "package org.junit;" +
      "import java.lang.annotation.*;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Ignore {}",
      "package org.junit;" +
      "import java.lang.annotation.*;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",
      "package org.junit.jupiter.api;" +
      "import java.lang.annotation.*;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",
      "package org.junit.platform.commons.annotation;" +
      "public @interface Testable {}"
    };
  }
}
