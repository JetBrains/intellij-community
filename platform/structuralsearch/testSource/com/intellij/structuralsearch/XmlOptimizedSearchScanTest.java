// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;

/**
 * @author Bas Leijdekkers
 */
public class XmlOptimizedSearchScanTest extends StructuralSearchTestCase {

  public void testSimple() {
    final String plan1 = findPlan("hello world!");
    assertEquals("[in text:hello][in text:world]", plan1);

    final String plan2 = findPlan("<html>");
    assertEquals("[in code:html]", plan2);
  }

  public void testVars() {
    final String plan = findPlan("<'_A{0,0}><b>freak <em>mulching</em> accident</b></'_A><br>");
    assertEquals("[in code:br]", plan);
  }

  public void testAttributes() {
    final String plan1 = findPlan("<'_a href=\"https://www.jetbrains.com\">");
    assertEquals("[in code:href][in code:https://www.jetbrains.com]", plan1);

    final String plan2 = findPlan("<a '_t{0,1}:[regex( href )]=\"https://www.jetbrains.com\">");
    assertEquals("[in code:a]", plan2);

    final String plan3 = findPlan("<a href=\"'_value{0,1}:[regex( xxx )]\">");
    assertEquals("[in code:a][in code:href]", plan3);
  }

  private String findPlan(final String s) {
    findMatchesCount("{}", s, StdFileTypes.HTML);
    return PatternCompiler.getLastFindPlan();
  }
}
