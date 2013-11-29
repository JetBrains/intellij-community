package com.intellij.json;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;

/**
 * @author Mikhail Golubev
 */
public class JsonCompletionTest extends CodeInsightFixtureTestCase {
  private static final String[] KEYWORDS = new String[]{"true", "false", "null"};
  private static final String[] NOTHING = ArrayUtil.EMPTY_STRING_ARRAY;

  @Override
  public void setUp() throws Exception {
    IdeaTestCase.initPlatformPrefix();
    super.setUp();
  }
  @Override
  protected String getBasePath() {
    return "/plugins/json-tests/testData/completion";
  }



  @Override
  protected boolean isCommunity() {
    return true;
  }

  public void testInsideArrayElement1() throws Exception {
    doTest(KEYWORDS);
  }

  public void testInsideArrayElement2() throws Exception {
    doTest(KEYWORDS);
  }

  public void testInsidePropertyKey1() throws Exception {
    doTest(NOTHING);
  }

  public void testInsidePropertyKey2() throws Exception {
    doTest(NOTHING);
  }

  public void testInsidePropertyValue() throws Exception {
    doTest(KEYWORDS);
  }

  private void doTest(String... variants) {
    myFixture.testCompletionVariants(getTestName(false) + ".json", variants);
  }
}
