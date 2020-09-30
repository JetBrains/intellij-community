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
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.testFrameworks.AssertWithoutMessageInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JUnitAssertsWithoutMessagesInspectionTest extends LightJavaInspectionTestCase {

  public void testFailWithMessage() {
    doTest();
  }

  public void testQuickFixAssertEquals() {
    doTest("import org.junit.Test;\n" +
           "import static org.junit.Assert.*;\n" +

           "class TestCase {\n" +
           "    @Test\n" +
           "    public void test() {\n" +
           "        <warning descr=\"'assertEquals()' without message\"><caret>assertEquals</warning>(1, 1);\n" +
           "    }\n" +
           "}");
    checkQuickFix("Add error message", "import org.junit.Test;\n" +
                                       "import static org.junit.Assert.*;\n" +

                                       "class TestCase {\n" +
                                       "    @Test\n" +
                                       "    public void test() {\n" +
                                       "        assertEquals(\"<caret>\", 1, 1);\n" +
                                       "    }\n" +
                                       "}");
  }

  public void testQuickFixFail() {
    doTest("import org.junit.Test;\n" +
           "import static org.junit.Assert.*;\n" +

           "class TestCase {\n" +
           "    @Test\n" +
           "    public void test() {\n" +
           "        <warning descr=\"'fail()' without message\"><caret>fail</warning>();\n" +
           "    }\n" +
           "}");
    checkQuickFix("Add error message", "import org.junit.Test;\n" +
                                       "import static org.junit.Assert.*;\n" +

                                       "class TestCase {\n" +
                                       "    @Test\n" +
                                       "    public void test() {\n" +
                                       "        fail(\"<caret>\");\n" +
                                       "    }\n" +
                                       "}");
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/junit/junit_asserts_without_messages";
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",
      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertEquals(double expected, double actual, double delta) {}" +
      "  static public void assertEquals(Object expected, Object actual){}" +
      "  static public void assertEquals(String message, Object expected, Object actual){}" +
      "  static public void assertSame(Object expected, Object actual){}" +
      "  static public void assertSame(String message, Object expected, Object actual){}" +
      "  static public void fail(){}" +
      "  static public void fail(String message){}" +
      "}",

      "package org.junit.jupiter.api;\n" +
      "public final class Assertions {\n" +
      "    public static void fail(String message) {}\n" +
      "}"
    };
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AssertWithoutMessageInspection();
  }
}