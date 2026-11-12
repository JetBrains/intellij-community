// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;

@TestApplication
public class SpockFormattingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(SpockTestBase.SPOCK_PROJECT);
  private final TestFixture<GroovyFormatterFixture> formatter =
    groovyFormatterFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/formatter/");

  @Test
  public void testSpockTableWithStringComment() { doTest(); }

  @Test
  public void testSpockTableWithComments() { doTest(); }

  @Test
  public void testSpockTableWithFullwidthCharacters() { doTest(); }

  @Test
  public void testSpockTableWithLongTableParts() { doTest(); }

  @Test
  public void testSpockTableSeparatedByUnderscores() { doTest(); }

  @Test
  public void testSpockTableWithUndefinedLabel() { doTest(); }

  @Test
  public void testSpockTableInCombinedBlock() { doTest(); }

  @Test
  public void testSpockTablePartialReformatTagsEnabled() {
    formatter.get().getTempSettings().FORMATTER_TAGS_ENABLED = true;
    doTestPartialReformat("'both args'");
  }

  @Test
  public void testSpockTablePartialReformatTagsDisabled() {
    formatter.get().getTempSettings().FORMATTER_TAGS_ENABLED = false;
    doTestPartialReformat("'both args'");
  }

  private void doTestPartialReformat(@NotNull String rowIdentifier) {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    List<String> strings = TestUtils.readInput(myFixture.getTestDataPath() + "spockTablePartialReformat.test");
    String before = strings.get(0);
    String expected = StringUtil.trimEnd(strings.get(1), "\n");
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    int start = before.indexOf(rowIdentifier);
    int end = before.indexOf('\n', start);
    formatter.get().doFormatRange(myFixture.getFile(), start, end < 0 ? before.length() : end);
    myFixture.checkResult(expected);
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    List<String> strings = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    formatter.get().checkFormatting(strings.get(0), StringUtil.trimEnd(strings.get(1), "\n"));
  }
}