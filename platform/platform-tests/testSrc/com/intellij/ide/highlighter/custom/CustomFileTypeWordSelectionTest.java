// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter.custom;

import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author yole
 */
public class CustomFileTypeWordSelectionTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testCustomFileTypeBraces() {
    doTest();
  }

  public void testCustomFileTypeQuotes() { doTest(); }

  public void testCustomFileTypeQuotesWithEscapes() { doTest(); }

  public void testCustomFileTypeSkipMatchedPair() {
    doTest();
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return "/platform/platform-tests/testData/selectWord/";
  }

  private void doTest() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, getTestName(true), "cs");
  }
}
