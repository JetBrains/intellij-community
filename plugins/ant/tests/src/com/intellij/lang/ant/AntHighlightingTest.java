// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.ant.dom.AntResolveInspection;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class AntHighlightingTest extends DaemonAnalyzerTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/highlighting/";
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean lowercaseFirstLetter) throws Exception {
    doTest(getTestName(lowercaseFirstLetter) + ".xml", false, false);
  }

  @Override
  protected void configureByFile(@TestDataFile @NonNls String filePath) throws Exception {
    super.configureByFile(filePath);
    AntSupport.markFileAsAntFile(myFile.getVirtualFile(), myFile.getProject(), true);
  }

  public void testEntity() throws Exception {
    configureByFiles(null, findVirtualFile(getTestName(false) + ".xml"), findVirtualFile(getTestName(false) + ".ent"));
    doDoTest(true, false);
  }

  public void testSanity() throws Exception {
    doTest(true);
  }

  public void testSanity2() throws Exception {
    doTest(true);
  }

  public void testRefid() throws Exception {
    doTest();
  }

  public void testRefidInCustomDomElement() throws Exception {
    doTest();
  }

  public void testExternalValidator() throws Exception {
    doTest();
  }

  public void testProperties() throws Exception {
    configureByFiles(null, findVirtualFile(getTestName(false) + ".xml"), findVirtualFile(getTestName(false) + ".properties"));
    doDoTest(true, false);
  }

  public void testProperties2() throws Exception {
    configureByFiles(null, findVirtualFile(getTestName(false) + ".xml"), findVirtualFile("yguard.jar"));
    doDoTest(true, false);
  }

  public void testEscapedProperties() throws Exception {
    configureByFiles(null, findVirtualFile(getTestName(false) + ".xml"));
    doDoTest(true, false);
  }

  public void testPropertiesFromFile() throws Exception {
    doTest();
  }

  public void testAntFileProperties() throws Exception {
    doTest();
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new AntDuplicateTargetsInspection(),
      new AntResolveInspection(),
      new XmlPathReferenceInspection(),
      new XmlUnresolvedReferenceInspection()
    };
  }
}
