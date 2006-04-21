/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.ant;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.ParsingTestCase;

public class AntParsingTest extends ParsingTestCase {

  public AntParsingTest() {
    super("", "ant");
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/ant/tests/data";
  }

  public void testSingleProject() throws Exception {
    doTest(true);
  }

  public void testProjectWithDefaultTarget() throws Exception {
    doTest(true);
  }

  public void testProjectWithDescriptions() throws Exception {
    doTest(true);
  }

  public void testProjectWithDependentTargets() throws Exception {
    doTest(true);
  }

  public void testSimpleProperties() throws Exception {
    doTest(true);
  }

  public void testPropertyLocation() throws Exception {
    doTest(true);
  }

  public void testPropertyFile() throws Exception {
    doTest(true);
  }

  public void testSimpleAntCall() throws Exception {
    doTest(true);
  }

  public void testAntCallNestedTargets() throws Exception {
    doTest(true);
  }
}