// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"EqualsBetweenInconvertibleTypes", "ResultOfMethodCallIgnored", "StringEqualsCharSequence"})
public class EqualsBetweenInconvertibleTypesInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doMemberTest("public void foo() {\n" +
                 "    final Integer foo = new Integer(3);\n" +
                 "    final Double bar = new Double(3);\n" +
                 "    foo./*'equals()' between objects of inconvertible types 'Integer' and 'Double'*/equals/**/(bar);\n" +
                 "}\n");
  }

  public void testWithoutQualifier() {
    doTest("class Clazz {\n" +
           "    void foo() {\n" +
           "        boolean bar = /*'equals()' between objects of inconvertible types 'Clazz' and 'String'*/equals/**/(\"differentClass\");\n" +
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
      "     return c1./*'equals()' between objects of inconvertible types 'Collection<Integer>' and 'Collection<String>'*/equals/**/(c2);" +
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

  public void testMethodReference() {
    doTest("import java.util.Objects;\n" +
           "import java.util.function.*;\n" +
           "\n" +
           "class Test {\n" +
           "  Predicate<Integer> p = \"123\"::/*'equals()' between objects of inconvertible types 'String' and 'Integer'*/equals/**/;\n" +
           "  Predicate<CharSequence> pOk = \"456\"::equals;\n" +
           "  BiPredicate<String, Integer> bp = Objects::/*'equals()' between objects of inconvertible types 'String' and 'Integer'*/equals/**/;\n" +
           "  BiPredicate<Long, Double> bp2 = Object::/*'equals()' between objects of inconvertible types 'Long' and 'Double'*/equals/**/;\n" +
           "  BiPredicate<Long, Long> bpOk = Object::equals;\n" +
           "}\n");
  }

  public void testNoCommonSubclass() {
    doTest("import java.util.Date;\n" +
           "import java.util.Map;\n" +
           "import java.util.Objects;\n" +
           "\n" +
           "class X {\n" +
           "  public static void foo(Date date, Map<String, String> map) {\n" +
           "    boolean res = Objects./*No class found which is a subtype of both 'Map<String, String>' and 'Date'*/equals/**/(map, date);\n" +
           "  }\n" +
           "}");
  }

  public void testNoCommonSubclassEqualityComparison() {
    doTest("import java.util.Date;\n" +
           "import java.util.Map;\n" +
           "import java.util.Objects;\n" +
           "\n" +
           "class X {\n" +
           "  public static boolean foo(Date date, Map<String, String> map) {\n" +
           "    return map /*No class found which is a subtype of both 'Map<String, String>' and 'Date'*/==/**/ date;\n" +
           "  }\n" +
           "}");
  }

  public void testCommonSubclass() {
    doTest("import java.util.Date;\n" +
           "import java.util.Map;\n" +
           "import java.util.Objects;\n" +
           "\n" +
           "class X {\n" +
           "  static abstract class Y extends Date implements Map<String, String> {}\n" +
           "  \n" +
           "  public static void foo(Date date, Map<String, String> map) {\n" +
           "    boolean res = Objects.equals(map, date);\n" +
           "  }\n" +
           "}");
  }

  public void testDifferentSets() {
    doTest("import java.util.*;\n" +
           "\n" +
           "class X {\n" +
           "  boolean test(HashSet<String> set1, TreeSet<String> set2) {\n" +
           "    return set1.equals(set2); // can be equal by content\n" +
           "  }\n" +
           "\n" +
           "  boolean test2(HashSet<String> set1, TreeSet<Integer> set2) {\n" +
           "    return set1./*'equals()' between objects of inconvertible types 'HashSet<String>' and 'TreeSet<Integer>'*/equals/**/(set2);\n" +
           "  }\n" +
           "}");
  }

  public void testGeneratedEquals() {
    doTest("class A {\n" +
           "    int i;\n" +
           "\n" +
           "    @Override\n" +
           "    public boolean equals(Object o) {\n" +
           "      if (this == o) return true;\n" +
           "      if (o == null || getClass() != o.getClass()) return false; // <-- a warning here is unexpected\n" +
           "      A a = (A)o;\n" +
           "      if (i != a.i) return false;\n" +
           "      return true;\n" +
           "    }\n" +
           "    @Override\n" +
           "    public int hashCode() {\n" +
           "      return i;\n" +
           "    }\n" +
           "  }");
  }

  public void testWilcards() {
    doTest("import java.util.*;" +
           "class X {" +
           "  boolean x(Class<? extends Date> a, Class<? extends Map<String, String>> b) {" +
           "    return b./*No class found which is a subtype of both 'Map<String, String>' and 'Date'*/equals/**/(a);" +
           "  }" +
           "}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
