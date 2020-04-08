// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;

/**
 * @author Maxim.Mossienko
 */
public class OptimizedSearchScanTest extends StructuralSearchTestCase {

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }

  public void testClassByQName() {
    doTest("A.f", "[in code:f][in code:A]");
  }

  public void testOptionalMethodWithThrowsClause() {
    doTest("exception should not be in plan",
           "class C {" +
           "    void '_m{0,1} () throws OMGWTFBBQException {}" +
           "}",
           "[in code:C][in code:class|in code:enum|in code:interface]");

    doTest("throws should not be in plan",
           "class C {" +
           "  String m() throws '_E{0,1} {" +
           "    System.out.println();" +
           "    return null;" +
           "  }" +
           "}",
           "[in code:C][in code:class|in code:enum|in code:interface][in code:m][in code:String][in code:println][in code:out]" +
           "[in code:System][in code:return][in code:null]");
  }

  public void testExtendsImplements() {
    doTest("extends should not be in plan",
           "class A extends '_B{0,0} {}", "[in code:A][in code:class|in code:enum|in code:interface]");

    doTest("implements should not be in plan",
           "class B implements '_I{0,0} {}", "[in code:B][in code:class|in code:enum|in code:interface]");
  }

  public void testLambda() {
    doTest(":: in plan", "'_Q::x", "[in code:::][in code:x]");
    doTest("-> in plan", "() -> {}", "[in code:->]");
  }

  public void testRegExpChar() {
    doTest("regex should not be in plan", "'x:[regex( a+ )]", "");
    doTest("regex should not be in plan 2", "'x:[ regex(a}) ]", "");
    doTest("plain text should be in plan", "'x:[ regex(aa) ]", "[in code:aa]");
  }

  public void testClasses() {
    doTest("class A {}", "[in code:A][in code:class|in code:enum|in code:interface]");
    doTest("interface I {}", "[in code:I][in code:interface]");
    doTest("enum E {}", "[in code:E][in code:enum]");
  }

  public void testDescendants() {
    doTest("classes outside search scope should also be added to descendants plan",
           "class '_A:*List {}",
           "[in code:AbstractList|in code:AbstractSequentialList|in code:ArrayList|" +
           "in code:CheckedList|in code:CheckedRandomAccessList|in code:CopiesList|in code:EmptyList|in code:List|" +
           "in code:SingletonList|in code:SubList|in code:SynchronizedList|in code:SynchronizedRandomAccessList|" +
           "in code:UnmodifiableList|in code:UnmodifiableRandomAccessList]" +
           "[in code:class|in code:enum|in code:interface]");

    doTest("non-existing class name should be added to plan", "enum '_E:*Zyxwvuts {}", "[in code:Zyxwvuts][in code:enum]");
  }

 public void testQualifiedReference() {
   doTest("new java.lang.RuntimeException('_x)", "[in code:new][in code:RuntimeException]");
   doTest("new java.lang.reflect.InvocationTargetException('_x)",
          "[in code:new][in code:InvocationTargetException][in code:reflect][in code:lang][in code:java]");
 }

 public void testTryWithoutCatch() {
   doTest("try {" +
          "  '_st*;" +
          "} catch ('_Type '_exception{0,0}) {" +
          "  '_st2*;" +
          "}",
          "[in code:try]");
 }

 public void testComment() {
   doTest("/* one/two (3|4|5) */", "[in comments:one][in comments:two][in comments:3][in comments:4][in comments:5]");
 }

 public void testPackageLocal() {
   doTest("@Modifier(\"packageLocal\") '_FieldType '_Field = '_Init?;", "");
 }

 public void testLiterals() {
   doTest("assert '_exp != null && true: \"'_exp is null\";",
          "[in literals:null][in literals:is][in code:assert][in code:null][in code:true]");
 }

 public void testStringLiteral() {
   doTest("\"asd fasdf\\\\n\"", "[in literals:fasdf][in literals:asd]");
 }

 public void testClassObjectAccessExpression() {
    doTest("ArrayUtil.toObjectArray($var$, $class$.class)", "[in code:toObjectArray][in code:ArrayUtil][in code:class]");
 }

  private void doTest(String query, String plan) {
    assertEquals(plan, getSearchPlan(query, StdFileTypes.JAVA));
  }

  private void doTest(String message, String query, String plan) {
    assertEquals(message, plan, getSearchPlan(query, StdFileTypes.JAVA));
  }
}
