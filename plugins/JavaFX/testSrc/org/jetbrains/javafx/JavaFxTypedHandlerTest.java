package org.jetbrains.javafx;

import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxTypedHandlerTest extends JavaFxLightFixtureTestCase {
  private void doTest(final char c) {
    final String name = "/typed/" + getTestName(false);
    myFixture.configureByFile(name + ".fx");
    myFixture.type(c);
    myFixture.checkResultByFile(name + "_after.fx");
  }

  public void testConcat1() {
    doTest('\n');
  }

  public void testConcat2() {
    doTest('\n');
  }

  public void testClosingQuote1() {
    doTest('\'');
  }

  public void testClosingQuote2() {
    doTest('\"');
  }

  public void testSimple1() {
    doTest('\'');
  }

  public void testSimple2() {
    doTest('\"');
  }

  public void testBeforeIdent1() {
    doTest('\'');
  }

  public void testBeforeIdent2() {
    doTest('\"');
  }

  public void testParens() {
    doTest('(');
  }

  public void testParenBeforeIdent() {
    doTest('(');
  }

  public void testBrackets() {
    doTest('[');
  }

  public void testBraces() {
    doTest('{');
  }

  public void testInFormat() {
    doTest('\"');
  }
}
