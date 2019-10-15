// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.search.ProjectScope;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.testFramework.IdeaTestUtil;

/**
 * @author Maxim.Mossienko
 */
public class OptimizedSearchScanTest extends StructuralSearchTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    options.setScope(ProjectScope.getAllScope(getProject()));
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }

  private String findWordsToBeUsedWhenSearchingFor(final String s) {
    findMatchesCount("{}", s);
    return PatternCompiler.getLastSearchPlan();
  }

  public void testClassByQName() {
    final String plan = findWordsToBeUsedWhenSearchingFor("A.f");
    assertEquals("[in code:f][in code:A]", plan);
  }

  public void testOptionalMethodWithThrowsClause() {
    final String plan = findWordsToBeUsedWhenSearchingFor(
      "class C {" +
      "    void '_m{0,1} () throws OMGWTFBBQException {}" +
      "}");
    assertEquals("exception should not be in plan", "[in code:C][in code:class|in code:enum|in code:interface]", plan);

    final String plan2 = findWordsToBeUsedWhenSearchingFor(
      "class C {" +
      "  String m() throws '_E{0,1} {" +
      "    System.out.println();" +
      "    return null;" +
      "  }" +
      "}");
    assertEquals("throws should not be in plan",
                 "[in code:C][in code:class|in code:enum|in code:interface][in code:m][in code:String][in code:println][in code:out]" +
                 "[in code:System][in code:return][in code:null]",
                 plan2);
  }

  public void testExtendsImplements() {
    final String plan1 = findWordsToBeUsedWhenSearchingFor("class A extends '_B{0,0} {}");
    assertEquals("extends should not be in plan", "[in code:A][in code:class|in code:enum|in code:interface]", plan1);

    final String plan2 = findWordsToBeUsedWhenSearchingFor("class B implements '_I{0,0} {}");
    assertEquals("implements should not be in plan", "[in code:B][in code:class|in code:enum|in code:interface]", plan2);
  }

  public void testLambda() {
    final String plan1 = findWordsToBeUsedWhenSearchingFor("'_Q::x");
    assertEquals(":: in plan", "[in code:::][in code:x]", plan1);

    final String plan2 = findWordsToBeUsedWhenSearchingFor("() -> {}");
    assertEquals("-> in plan", "[in code:->]", plan2);
  }

  public void testRegExpChar() {
    final String plan = findWordsToBeUsedWhenSearchingFor("'x:[regex( a+ )]");
    assertEquals("regex should not be in plan", "", plan);

    final String plan2 = findWordsToBeUsedWhenSearchingFor("'x:[ regex(a}) ]");
    assertEquals("regex should not be in plan 2", "", plan2);

    final String plan3 = findWordsToBeUsedWhenSearchingFor("'x:[ regex(aa) ]");
    assertEquals("plain text should be in plan", "[in code:aa]", plan3);
  }

  public void testClasses() {
    final String plan1 = findWordsToBeUsedWhenSearchingFor("class A {}");
    assertEquals("[in code:A][in code:class|in code:enum|in code:interface]", plan1);
    final String plan2 = findWordsToBeUsedWhenSearchingFor("interface I {}");
    assertEquals("[in code:I][in code:interface]", plan2);
    final String plan3 = findWordsToBeUsedWhenSearchingFor("enum E {}");
    assertEquals("[in code:E][in code:enum]", plan3);
  }

  public void testDescendants() {
    final String plan = findWordsToBeUsedWhenSearchingFor("class '_A:*List {}");
    assertEquals("classes outside search scope should alse be added to descendants plan",
                 "[in code:AbstractList|in code:AbstractSequentialList|in code:ArrayList|" +
                 "in code:CheckedList|in code:CheckedRandomAccessList|in code:CopiesList|in code:EmptyList|in code:List|" +
                 "in code:SingletonList|in code:SubList|in code:SynchronizedList|in code:SynchronizedRandomAccessList|" +
                 "in code:UnmodifiableList|in code:UnmodifiableRandomAccessList]" +
                 "[in code:class|in code:enum|in code:interface]", plan);

    final String plan2 = findWordsToBeUsedWhenSearchingFor("enum '_E:*Zyxwvuts {}");
    assertEquals("non-existing class name should be added to plan", "[in code:Zyxwvuts][in code:enum]", plan2);
  }

 public void testQualifiedReference() {
   final String plan = findWordsToBeUsedWhenSearchingFor("new java.lang.RuntimeException('_x)");
   assertEquals("[in code:new][in code:RuntimeException]", plan);

   final String plan2 = findWordsToBeUsedWhenSearchingFor("new java.lang.reflect.InvocationTargetException('_x)");
   assertEquals("[in code:new][in code:InvocationTargetException][in code:reflect][in code:lang][in code:java]", plan2);
 }

 public void testTryWithoutCatch() {
   final String plan = findWordsToBeUsedWhenSearchingFor("try {" +
                                                         "  '_st*;" +
                                                         "} catch ('_Type '_exception{0,0}) {" +
                                                         "  '_st2*;" +
                                                         "}");
   assertEquals("[in code:try]", plan);
 }

 public void testComment() {
   final String plan = findWordsToBeUsedWhenSearchingFor("/* one/two (3|4|5) */");
   assertEquals("[in comments:one][in comments:two][in comments:3][in comments:4][in comments:5]", plan);
 }

 public void testPackageLocal() {
   final String plan = findWordsToBeUsedWhenSearchingFor("@Modifier(\"packageLocal\") '_FieldType '_Field = '_Init?;");
   assertEquals("", plan);
 }

 public void testLiterals() {
   final String plan = findWordsToBeUsedWhenSearchingFor("assert '_exp != null && true: \"'_exp is null\";");
   assertEquals("[in literals:null][in literals:is][in code:assert][in code:null][in code:true]", plan);
 }

 public void testStringLiteral() {
   final String plan = findWordsToBeUsedWhenSearchingFor("\"asd fasdf\\\\n\"");
   assertEquals("[in literals:fasdf][in literals:asd]", plan);
 }

 public void testClassObjectAccessExpression() {
    final String plan = findWordsToBeUsedWhenSearchingFor("ArrayUtil.toObjectArray($var$, $class$.class)");
    assertEquals("[in code:toObjectArray][in code:ArrayUtil][in code:class]", plan);
 }
}
