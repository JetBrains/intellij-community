// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import org.jetbrains.plugins.groovy.util.BaseTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Test;

public class GroovyBraceMatcherTest extends GroovyLatestTest implements BaseTest {
  private void doTest(String before, String charsToType, String after) {
    configureByText(before);
    getFixture().type(charsToType);
    getFixture().checkResult(after);
  }

  @Test
  public void left_parenthesis() {
    doTest("foo<caret>", "(", "foo(<caret>)");// before eof
    doTest("foo<caret> ", "(", "foo(<caret>) ");// before whitespace
    doTest("foo<caret>\n", "(", "foo(<caret>)\n");
    doTest("foo<caret>)", "(", "foo(<caret>)");
    doTest("foo<caret>]", "(", "foo(<caret>)]");
    doTest("[foo<caret>]", "(", "[foo(<caret>)]");
    doTest("[foo<caret>)", "(", "[foo(<caret>)");
    doTest("(foo<caret>]", "(", "(foo(<caret>)]");
    doTest("(foo<caret>)", "(", "(foo(<caret>))");
  }

  @Test
  public void left_bracket() {
    doTest("foo<caret>", "[", "foo[<caret>]");// before eof
    doTest("foo<caret> ", "[", "foo[<caret>] ");// before whitespace
    doTest("foo<caret>\n", "[", "foo[<caret>]\n");
    doTest("foo<caret>)", "[", "foo[<caret>])");
    doTest("foo<caret>]", "[", "foo[<caret>]");
    doTest("[foo<caret>]", "[", "[foo[<caret>]]");
    doTest("[foo<caret>)", "[", "[foo[<caret>])");
    doTest("(foo<caret>]", "[", "(foo[<caret>]");
    doTest("(foo<caret>)", "[", "(foo[<caret>])");
  }
}
