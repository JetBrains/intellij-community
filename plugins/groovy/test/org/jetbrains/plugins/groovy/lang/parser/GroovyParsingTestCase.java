// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Iterator;

public abstract class GroovyParsingTestCase extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public String getBasePath() {
    return TestUtils.getTestDataPath() + "parsing/groovy/";
  }

  public void doTest() {
    doTest(getTestName(true).replace("$", "/") + ".test");
  }

  protected void doTest(String fileName) {
    String path = getTestDataPath() + "/" + fileName;
    final Iterator<String> iterator = TestUtils.readInput(path).iterator();
    String input = iterator.hasNext() ? iterator.next() : null;

    checkParsing(input, fileName);
  }

  protected void checkParsing(String input, String path) {
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(getProject(), input);
    final String psiTree = DebugUtil.psiToString(psiFile, true);
    final String prefix = input + "\n-----\n";
    myFixture.configureByText("test.txt", prefix + psiTree.trim());
    myFixture.checkResultByFile(path, false);
  }

  protected void checkParsingByText(String input, String output) {
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(getProject(), input);
    final String psiTree = DebugUtil.psiToString(psiFile, true);
    final String prefix = input.trim() + "\n-----\n";
    TestCase.assertEquals(prefix + output.trim(), prefix + psiTree.trim());
  }
}
