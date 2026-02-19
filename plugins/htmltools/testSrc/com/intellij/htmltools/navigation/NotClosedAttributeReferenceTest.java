package com.intellij.htmltools.navigation;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class NotClosedAttributeReferenceTest extends BasePlatformTestCase {
  public void testXHtmlCompletion() {
    myFixture.configureByText("test.html", "<p id=<caret>\"");
    try {
      myFixture.getReferenceAtCaretPosition();
    } catch (Exception e) {
      fail();
    }
  }
}
