/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch;

import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;

/**
 * @author Maxim.Mossienko
 */
public class OptimizedSearchScanTest extends StructuralSearchTestCase {

  public void testClassByQName() {
    final String plan = findWordsToBeUsedWhenSearchingFor("A.f");
    assertEquals("[in code:f][in code:A]", plan);
  }

  public void testOptionalMethodWithThrowsClause() {
    final String plan = findWordsToBeUsedWhenSearchingFor("class C {" +
                                                          "    void 'm{0,1} () throws OMGWTFBBQException {}" +
                                                          "}");
    assertEquals("exception should not be in plan", "[in code:C]", plan);
  }

  public void testRegExpChar() {
    final String plan = findWordsToBeUsedWhenSearchingFor("'x:[regex( a+ )]");
    assertEquals("", plan);

    final String plan2 = findWordsToBeUsedWhenSearchingFor("'x:[ regex(a}) ]");
    assertEquals("", plan2);
  }

  private String findWordsToBeUsedWhenSearchingFor(final String s) {
    findMatchesCount("{}",s);
    return PatternCompiler.getLastFindPlan();
  }
}
