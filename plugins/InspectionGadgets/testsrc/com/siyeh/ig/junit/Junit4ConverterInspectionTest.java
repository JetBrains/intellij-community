// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class Junit4ConverterInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package junit.framework;" +
      "public abstract class TestCase extends Assert {\n" +
      "}",

      """
package junit.framework;public class Assert { static public void assertEquals(int expected, int actual) {
}
}""",

      "package org.junit;" +
      "public class Assert {" +
      "static public void assertEquals(long expected, long actual) {" +
      "}\n" +
      "}",

      """
package org.junit;@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Test {}"""
    };
  }

  @SuppressWarnings("JUnitTestCaseWithNoTests")
  public void testSimple() {
    doTest("""
             import junit.framework.TestCase;
             class /*'JUnit3Test' could be converted to JUnit4 test case*//*_*/JUnit3Test/**/ extends TestCase {
                 public void testAddition() {
                     assertEquals(2, 1 + 1);
                 }
             }""");
    checkQuickFix("Convert to JUnit 4 test case", """
      import junit.framework.TestCase;
      import org.junit.Assert;
      import org.junit.Test;

      class JUnit3Test {
          @Test
          public void testAddition() {
              Assert.assertEquals(2, 1 + 1);
          }
      }""");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new Junit4ConverterInspection();
  }
}
