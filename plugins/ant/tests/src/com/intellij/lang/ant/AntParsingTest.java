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

  public void testAntComments() throws Exception {
    doTest(true);
  }

  public void testPrologEpilogue() throws Exception {
    doTest(true);
  }

  public void testMacroDef() throws Exception {
    doTest(true);
  }

  public void testPresetDef() throws Exception {
    doTest(true);
  }

  public void testForwardMacroDef() throws Exception {
    doTest(true);
  }

  public void testSequentialParallel() throws Exception {
    doTest(true);
  }

  public void testAvailable() throws Exception {
    doTest(true);
  }

  public void testChecksum() throws Exception {
    doTest(true);
  }

  public void testChecksum1() throws Exception {
    doTest(true);
  }

  public void testCondition() throws Exception {
    doTest(true);
  }

  public void testUptodate() throws Exception {
    doTest(true);
  }

  public void testDirname() throws Exception {
    doTest(true);
  }

  public void testBasename() throws Exception {
    doTest(true);
  }

  public void testLoadFile() throws Exception {
    doTest(true);
  }

  public void testTempFile() throws Exception {
    doTest(true);
  }

  public void testLength() throws Exception {
    doTest(true);
  }

  public void testPathConvert() throws Exception {
    doTest(true);
  }

  public void testWhichResource() throws Exception {
    doTest(true);
  }

  public void testP4Counter() throws Exception {
    doTest(true);
  }

  public void testJarLibResolve() throws Exception {
    doTest(true);
  }

  public void testAntTask() throws Exception {
    doTest(true);
  }

  public void testJava() throws Exception {
    doTest(true);
  }
}