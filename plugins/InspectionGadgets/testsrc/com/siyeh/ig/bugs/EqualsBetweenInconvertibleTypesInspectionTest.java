/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author Bas Leijdekkers
 */
public class EqualsBetweenInconvertibleTypesInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doMemberTest("public void foo() {\n" +
                 "    final Integer foo = new Integer(3);\n" +
                 "    final Double bar = new Double(3);\n" +
                 "    foo./*'equals()' between objects of inconvertible types 'Double' and 'Integer'*/equals/**/(bar);\n" +
                 "}\n");
  }

  public void testWithoutQualifier() {
    doTest("class Clazz {\n" +
           "    void foo() {\n" +
           "        boolean bar = /*'equals()' between objects of inconvertible types 'String' and 'Clazz'*/equals/**/(\"differentClass\");\n" +
           "    }\n" +
           "}");
  }

  public void testJavaUtilObjectsEquals() {
    doStatementTest("java.util.Objects./*'equals()' between objects of inconvertible types 'Integer' and 'String'*/equals/**/(Integer.valueOf(1), \"string\");");
  }

  public void testComGoogleCommonBaseObjects() {
    doStatementTest("com.google.common.base.Objects./*'equal()' between objects of inconvertible types 'Integer' and 'String'*/equal/**/(Integer.valueOf(1), \"string\");");
  }

  public void testCollection() {
    doTest(
      "import java.util.Collection;" +
      "class XXX {" +
      "  interface A {}" +
      "  interface B extends A {}" +
      "  boolean m(Collection<A> c1, Collection<B> c2) {" +
      "    return c2.equals(c1);" +
      "  }" +
      "" +
      "  boolean n(Collection<Integer> c1, Collection<String> c2) {" +
      "     return c1./*'equals()' between objects of inconvertible types 'Collection<String>' and 'Collection<Integer>'*/equals/**/(c2);" +
      "  }" +
      "}");
  }
  
  public void testRaw() {
    doTest(
      "import java.util.Collection;" +
      "class XXX {" +
      "  interface A {}" +
      "  boolean m(Collection c1, Collection<A> c2) {" +
      "    return c2.equals(c1);" +
      "  }" +
      "}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public final class Objects {" +
      "  public static boolean equals(Object a, Object b) {" +
      "    return (a == b) || (a != null && a.equals(b));" +
      "  }" +
      "}",
      "package com.google.common.base;" +
      "public final class Objects {" +
      "  public static boolean equal(Object a, Object b) {" +
      "    return true;" +
      "  }" +
      "}"
    };
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new EqualsBetweenInconvertibleTypesInspection();
  }
}
