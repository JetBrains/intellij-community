// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class SpockFormattingTest extends GroovyFormatterTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/formatter/";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return SpockTestBase.SPOCK_PROJECT;
  }

  public void testSpockTableWithStringComment() { doTest(); }

  public void testSpockTableWithComments() { doTest(); }

  public void testSpockTableWithFullwidthCharacters() { doTest(); }

  public void testSpockTableWithLongTableParts() { doTest(); }

  public void testSpockTableSeparatedByUnderscores() { doTest(); }

  public void testSpockTableWithUndefinedLabel() { doTest(); }

  public void testSpockTableInCombinedBlock() { doTest(); }

  public void testSpockTablePartialReformatTagsEnabled() {
    myTempSettings.FORMATTER_TAGS_ENABLED = true;
    doTestPartialReformat("'both args'");
  }

  public void testSpockTablePartialReformatTagsDisabled() {
    myTempSettings.FORMATTER_TAGS_ENABLED = false;
    doTestPartialReformat("'both args'");
  }

  private void doTestPartialReformat(@NotNull String rowIdentifier) {
    List<String> strings = TestUtils.readInput(getTestDataPath() + "spockTablePartialReformat.test");
    String before = strings.get(0);
    String expected = StringUtil.trimEnd(strings.get(1), "\n");
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    int start = before.indexOf(rowIdentifier);
    int end = before.indexOf('\n', start);
    doFormatRange(myFixture.getFile(), start, end < 0 ? before.length() : end);
    myFixture.checkResult(expected);
  }

  public void doTest() {
    List<String> strings = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    checkFormatting(strings.get(0), StringUtil.trimEnd(strings.get(1), "\n"));
  }
}