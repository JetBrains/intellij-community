// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Iterator;

/**
 * The helpers of the Groovy surround tests.
 */
public final class SurroundTestUtil {
  private SurroundTestUtil() {
  }

  /**
   * Reads the text before and after from {@code <test name>.test} in the test data, and checks the surrounder with them.
   */
  public static void doTest(LightJavaCodeInsightTestFixture fixture, final Surrounder surrounder) {
    final Iterator<String> iterator = TestUtils.readInput(fixture.getTestDataPath() + "/" + fixture.getTestName(true) + ".test").iterator();
    String before = iterator.hasNext() ? iterator.next() : null;
    String after = iterator.hasNext() ? iterator.next() : null;
    doTest(fixture, surrounder, before, after);
  }

  /**
   * Configures {@code a.groovy} with {@code textBefore}, runs the surrounder, and compares the file with {@code textAfter}.
   */
  public static void doTest(LightJavaCodeInsightTestFixture fixture, final Surrounder surrounder, String textBefore, String textAfter) {
    fixture.configureByText("a.groovy", textBefore);
    EdtTestUtil.runInEdtAndWait(() -> SurroundWithHandler.invoke(fixture.getProject(), fixture.getEditor(), fixture.getFile(), surrounder));
    fixture.checkResult(textAfter);
  }
}
