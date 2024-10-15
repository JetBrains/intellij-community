// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
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

  public void doTest() {
    List<String> strings = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    checkFormatting(strings.get(0), StringUtil.trimEnd(strings.get(1), "\n"));
  }
}