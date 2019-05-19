// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    final String plan1 = findPlan("<'_a title=\"fiber\">");
    assertEquals("[in code:title][in code:fiber]", plan1);

    final String plan2 = findPlan("<a '_t{0,1}:[regex( href )]=\"https://www.jetbrains.com\">");
    assertEquals("[in code:a]", plan2);

    final String plan3 = findPlan("<a href=\"'_value{0,1}:[regex( xxx )]\">");
    assertEquals("[in code:a][in code:href]", plan3);
  }

  public void testElementsThatAreNotWords() {
    final String plan1 = findPlan("<TextView\n" +
                                 "\t\tandroid:id=\"@+id/text\"\n" +
                                 "\t\tandroid:layout_width=\"wrap_content\"\n" +
                                 "\t\tandroid:fontFamily=\"sans-serif-medium\"\n" +
                                 "\t\tandroid:layout_height=\"wrap_content\" />");
    assertEquals("[in code:TextView][in code:android][in code:id][in code:text][in code:layout_width][in code:wrap_content]" +
                 "[in code:fontFamily][in code:medium][in code:serif][in code:sans][in code:layout_height]", plan1);

    final String plan2 = findPlan("<idea-plugin url=\"https://www.jetbrains.com\">");
    assertEquals("[in code:plugin][in code:idea][in code:url][in code:jetbrains][in code:https][in code:www][in code:com]", plan2);
  }

  private String findPlan(final String s) {
    findMatchesCount("{}", s, StdFileTypes.HTML);
    return PatternCompiler.getLastSearchPlan();
  }
}
