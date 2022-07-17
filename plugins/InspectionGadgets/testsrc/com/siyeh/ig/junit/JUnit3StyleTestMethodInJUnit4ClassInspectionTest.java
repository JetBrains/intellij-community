// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
      "import org.junit.platform.commons.annotation.Testable;" +
      "import java.lang.annotation.*;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "@Testable " +
      "public @interface Test {}",
      "package org.junit.platform.commons.annotation;" +
      "public @interface Testable {}"
    };
  }
}
