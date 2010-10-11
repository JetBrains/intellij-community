package org.jetbrains.javafx;

import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxHighlightingTest extends JavaFxLightFixtureTestCase {
  private void doTest() {
    final String testPath = "/highlighting/";
    myFixture.testHighlighting(true, true, false, testPath + getTestName(false) + ".fx");
  }

  public void testUnterminatedString() {
    doTest();
  }

  public void testLargeInteger() {
    doTest();
  }

  public void testLargeNumber() {
    doTest();
  }

  public void testBreak() {
    doTest();
  }

  public void testContinue() {
    doTest();
  }

  public void testAttribute() {
    doTest();
  }

  public void testPrivate() {
    doTest();
  }

  public void testStatic() {
    doTest();
  }
}
