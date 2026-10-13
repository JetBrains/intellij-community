// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.ant;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

@TestApplication
public class AntParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PluginPathManager.getPluginHomePath("ant") + "/tests/data/psi", "ant", new XMLParserDefinition());

  @Test
  public void testSingleProject() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testProjectWithDefaultTarget() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testProjectWithDescriptions() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testProjectWithDependentTargets() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testSimpleProperties() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testPropertyLocation() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testPropertyFile() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testSimpleAntCall() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAntCallNestedTargets() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAntComments() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testPrologEpilogue() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testMacroDef() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testPresetDef() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testForwardMacroDef() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testSequentialParallel() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAvailable() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testChecksum() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testChecksum1() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCondition() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testUptodate() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testDirname() throws Exception {
    parsingFixture.get().doTest(SystemInfo.isWindows ? "_w" : "_u");
  }

  @Test
  public void testBasename() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testLoadFile() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testTempFile() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testLength() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testPathConvert() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testWhichResource() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testP4Counter() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testJarLibResolve() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAntTask() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testJava() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testBuildNumber() {
    parsingFixture.get().doTest(true);
  }
}