/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author peter
 */
public abstract class GroovyParsingTestCase extends LightCodeInsightFixtureTestCase{

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "parsing/groovy/";
  }

  public void doTest() {
    doTest(getTestName(true).replace('$', '/') + ".test");
  }

  protected void doTest(String fileName) {
    final List<String> list = TestUtils.readInput(getTestDataPath() + "/" + fileName);

    final String input = list.get(0);
    final String output = list.get(1);
    checkParsing(input, output);
  }

  protected void checkParsing(String input, String output) {
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(getProject(), input);
    String psiTree = DebugUtil.psiToString(psiFile, false);
    assertEquals(output.trim(), psiTree.trim());
  }
}
