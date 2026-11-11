// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GroovyBraceMatcherTest implements BaseTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private void doTest(String before, String charsToType, String after) {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByText(before);
      getFixture().type(charsToType);
      getFixture().checkResult(after);
    });
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
