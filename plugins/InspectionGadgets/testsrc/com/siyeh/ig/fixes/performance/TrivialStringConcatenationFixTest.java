package com.siyeh.ig.fixes.performance;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.TrivialStringConcatenationInspection;

public class TrivialStringConcatenationFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TrivialStringConcatenationInspection());
    myRelativePath = "performance/trivial_string_concatenation";
  }

  public void testParentheses() { doTest("Replace concatenation with 'completedTiles + \" , \" + (totalTiles - completedTiles)'"); }
  public void testParentheses2() { doTest("Replace concatenation with '\" (\" + \"Groovy \" + (version) + \")\"'"); }
  public void testBinaryNull() { doTest("Replace concatenation with 'String.valueOf((Object)null)'"); }
  public void testAtTheEnd() { doTest("Replace concatenation with '\"asdf\" + 1 + o'"); }
}