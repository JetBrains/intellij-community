package com.intellij.structuralsearch;

import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;

/**
 * @author Maxim.Mossienko
 */
public class OptimizedSearchScanTest extends StructuralSearchTestCase {

  public void testClassByQName() throws Exception {
    final String plan = findWordsToBeUsedWhenSearchingFor("A.f");
    assertEquals("[in code:f][in code:A]", plan);
  }

  public void testOptionalMethodWithThrowsClause() {
    final String plan = findWordsToBeUsedWhenSearchingFor("class C {" +
                                                          "    void 'm{0,1} () throws OMGWTFBBQException {}" +
                                                          "}");
    assertEquals("exception should not be in plan", "[in code:C]", plan);
  }

  private String findWordsToBeUsedWhenSearchingFor(final String s) {
    findMatchesCount("{}",s);
    return PatternCompiler.getLastFindPlan();
  }
}
