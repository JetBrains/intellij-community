// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.ant.dom.AntResolveInspection;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;

/**
 * @author Maxim.Mossienko
 */
@TestApplication
public class AntHighlightingTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture =
    daemonAnalyzerFixture(getTestDataPath(), AntHighlightingTest::configureLocalInspectionTools);

  private static @NotNull String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/highlighting/";
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean lowercaseFirstLetter) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    configureByFile(fixture.getTestName(lowercaseFirstLetter) + ".xml");
    fixture.doDoTest(false, false);
  }

  private void configureByFile(@NotNull String filePath) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(filePath);
    PsiFile myFile = fixture.getFile();
    EdtTestUtil.runInEdtAndWait(() -> AntSupport.markFileAsAntFile(myFile.getVirtualFile(), myFile.getProject(), true));
  }

  @Test
  public void testEntity() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.findVirtualFile(fixture.getTestName(false) + ".xml"), fixture.findVirtualFile(fixture.getTestName(false) + ".ent"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testSanity() {
    doTest(true);
  }

  @Test
  public void testSanity2() {
    doTest(true);
  }

  @Test
  public void testRefid() {
    doTest();
  }

  @Test
  public void testRefidInCustomDomElement() {
    doTest();
  }

  @Test
  public void testExternalValidator() {
    doTest();
  }

  @Test
  public void testProperties() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.findVirtualFile(fixture.getTestName(false) + ".xml"), fixture.findVirtualFile(fixture.getTestName(false) + ".properties"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testProperties2() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.findVirtualFile(fixture.getTestName(false) + ".xml"), fixture.findVirtualFile("yguard.jar"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testEscapedProperties() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.findVirtualFile(fixture.getTestName(false) + ".xml"));
    fixture.doDoTest(true, false);
  }

  @Test
  public void testPropertiesFromFile() {
    doTest();
  }

  @Test
  public void testAntFileProperties() {
    doTest();
  }

  private static LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new AntDuplicateTargetsInspection(),
      new AntResolveInspection(),
      new XmlPathReferenceInspection(),
      new XmlUnresolvedReferenceInspection()
    };
  }
}
