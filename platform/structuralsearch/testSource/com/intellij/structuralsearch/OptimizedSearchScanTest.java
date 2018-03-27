// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public void testClassByQName() {
    final String plan = findWordsToBeUsedWhenSearchingFor("A.f");
    assertEquals("[in code:f][in code:A]", plan);
  }

  public void testOptionalMethodWithThrowsClause() {
    final String plan = findWordsToBeUsedWhenSearchingFor(
      "class C {" +
      "    void 'm{0,1} () throws OMGWTFBBQException {}" +
      "}");
    assertEquals("exception should not be in plan", "[in code:class|in code:enum|in code:interface][in code:C]", plan);

    final String plan2 = findWordsToBeUsedWhenSearchingFor(
      "class C {" +
      "  String m() throws 'E{0,1} {" +
      "    System.out.println();" +
      "    return null;" +
      "  }" +
      "}");
    assertEquals("throws should not be in plan",
                 "[in code:class|in code:enum|in code:interface][in code:C][in code:m][in code:String][in code:println][in code:out]" +
                 "[in code:System][in code:return]",
                 plan2);
  }

  public void testExtendsImplements() {
    final String plan1 = findWordsToBeUsedWhenSearchingFor("class A extends '_B{0,0} {}");
    assertEquals("extends should not be in plan", "[in code:class|in code:enum|in code:interface][in code:A]", plan1);

    final String plan2 = findWordsToBeUsedWhenSearchingFor("class B implements '_I{0,0} {}");
    assertEquals("implements should not be in plan", "[in code:class|in code:enum|in code:interface][in code:B]", plan2);
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
    assertEquals("[in code:class|in code:enum|in code:interface][in code:A]", plan1);
    final String plan2 = findWordsToBeUsedWhenSearchingFor("interface I {}");
    assertEquals("[in code:interface][in code:I]", plan2);
    final String plan3 = findWordsToBeUsedWhenSearchingFor("enum E {}");
    assertEquals("[in code:enum][in code:E]", plan3);
  }

  public void testDescendants() {
    final String plan = findWordsToBeUsedWhenSearchingFor("class '_A:*List {}");
    assertEquals("classes outside search scope should alse be added to descendants plan",
                 "[in code:class|in code:enum|in code:interface][in code:AbstractList|in code:AbstractSequentialList|in code:ArrayList|" +
                 "in code:CheckedList|in code:CheckedRandomAccessList|in code:CopiesList|in code:EmptyList|in code:List|" +
                 "in code:SingletonList|in code:SubList|in code:SynchronizedList|in code:SynchronizedRandomAccessList|" +
                 "in code:UnmodifiableList|in code:UnmodifiableRandomAccessList]", plan);

    final String plan2 = findWordsToBeUsedWhenSearchingFor("enum '_E:*Zyxwvuts {}");
    assertEquals("non-existing class name should be added to plan", "[in code:enum][in code:Zyxwvuts]", plan2);
  }

  private String findWordsToBeUsedWhenSearchingFor(final String s) {
    findMatchesCount("{}", s);
    return PatternCompiler.getLastFindPlan();
  }
}
