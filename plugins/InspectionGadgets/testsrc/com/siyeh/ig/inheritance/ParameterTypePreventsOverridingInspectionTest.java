/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ParameterTypePreventsOverridingInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("package c;" +
           "import a.*;" +
           "import b.String;" +
           "class Sub extends Super {" +
           "  void m(/*Parameter type 'String' is located in 'b' while super method parameter type is located in 'java.lang' preventing overriding*/String/**/ s) {}" +
           "}");
  }

  public void testNonMatchingReturnType() {
    doTest("package c;" +
           "import a.*;" +
           "import b.String;" +
           "class Sub extends Super {" +
           "  int m(String s) {" +
           "    return -1;" +
           "  }" +
           "  class String {}" +
           "}");
  }

  public void testMatchingReturnType() {
    doTest("package c;" +
           "import a.*;" +
           "import b.String;" +
           "class Sub extends Super {" +
           "  Integer n(/*Parameter type 'String' is located in 'b' while super method parameter type is located in 'java.lang' preventing overriding*/String/**/ s, " +
           "            /*Parameter type 'String' is located in 'b' while super method parameter type is located in 'java.lang' preventing overriding*/String/**/ t) {" +
           "    return null;" +
           "  }" +
           "}");
  }

  public void testTypeParameter1() {
    doTest("package c;" +
           "import a.*;" +
           "class Y<T> extends X<T> {" +
           "  void m(T t) {}" +
           "}");
  }

  public void testTypeParameter2() {
    doTest("package c;" +
           "import a.*;" +
           "class Y<T extends String> extends X {" +
           "  void m(T t) {}" +
           "}");
  }

  public void testStaticmethod() {
    doTest("import java.util.List;" +
           "class ParameterTypePreventsOverridingFalsePositiveForStaticMethods {\n" +
           "    public static class SuperClass {\n" +
           "        public static <T> Object wrap( List<T> something ) {\n" +
           "            return null;\n" +
           "        }\n" +
           "    }\n" +
           "    public static class SubClass extends SuperClass {\n" +
           "        public static <T> Object wrap( List<T> something ) {\n" +
           "            return null;\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package a;" +
      "public class Super {" +
      "  void m(String s) {}" +
      "  Object n(String s, String t) {" +
      "    return null;" +
      "  }" +
      "}",
      "package a;" +
      "public class X<T> {" +
      "  void m(T t) {}" +
      "}",
      "package b;" +
      "public class String {}"
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ParameterTypePreventsOverridingInspection();
  }
}