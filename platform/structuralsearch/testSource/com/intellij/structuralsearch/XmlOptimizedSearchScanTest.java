// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.HtmlFileType;

/**
 * @author Bas Leijdekkers
 */
public class XmlOptimizedSearchScanTest extends StructuralSearchTestCase {

  public void testSimple() {
    doTest("hello world!", "[in text:hello][in text:world]");
    doTest("<html>", "[in code:html]");
  }

  public void testVars() {
    doTest("<'_A{0,0}><b>freak <em>mulching</em> accident</b></'_A><br>", "[in code:br]");
  }

  public void testAttributes() {
    doTest("<'_a title=\"fiber\">", "[in code:title][in code:fiber]");
    doTest("<a '_t{0,1}:[regex( href )]=\"https://www.jetbrains.com\">", "[in code:a]");
    doTest("<a href=\"'_value{0,1}:[regex( xxx )]\">", "[in code:a][in code:href]");
  }

  public void testElementsThatAreNotWords() {
    doTest("<TextView\n" +
           "\t\tandroid:id=\"@+id/text\"\n" +
           "\t\tandroid:layout_width=\"wrap_content\"\n" +
           "\t\tandroid:fontFamily=\"sans-serif-medium\"\n" +
           "\t\tandroid:layout_height=\"wrap_content\" />",
           "[in code:TextView][in code:android][in code:id][in code:text][in code:layout_width][in code:wrap_content]" +
           "[in code:fontFamily][in code:medium][in code:serif][in code:sans][in code:layout_height]");

    doTest("<idea-plugin url=\"https://www.jetbrains.com\">",
           "[in code:plugin][in code:idea][in code:url][in code:jetbrains][in code:https][in code:www][in code:com]");
  }

  private void doTest(String query, String plan) {
    assertEquals(plan, getSearchPlan(query, HtmlFileType.INSTANCE));
  }
}
