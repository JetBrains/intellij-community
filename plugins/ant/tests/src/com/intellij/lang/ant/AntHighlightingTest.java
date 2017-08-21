/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.ant.dom.AntResolveInspection;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @by Maxim.Mossienko
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class AntHighlightingTest extends DaemonAnalyzerTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data/highlighting/";
  }

  private boolean myIgnoreInfos;

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
    configureByFiles(null, getVirtualFile(getTestName(false) + ".xml"), getVirtualFile(getTestName(false) + ".ent"));
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
    configureByFiles(null, getVirtualFile(getTestName(false) + ".xml"), getVirtualFile(getTestName(false) + ".properties"));
    doDoTest(true, false);
  }

  public void testProperties2() throws Exception {
    configureByFiles(null, getVirtualFile(getTestName(false) + ".xml"), getVirtualFile("yguard.jar"));
    doDoTest(true, false);
  }

  public void testEscapedProperties() throws Exception {
    configureByFiles(null, getVirtualFile(getTestName(false) + ".xml"));
    doDoTest(true, false);
  }

  public void testPropertiesFromFile() throws Exception {
    doTest();
  }

  public void testAntFileProperties() throws Exception {
    doTest();
  }

  public void testBigFilePerformance() {
    try {
      myIgnoreInfos = true;
      PlatformTestUtil.startPerformanceTest("Big ant file highlighting", 15_000, () -> {
        configureByFiles(null, getVirtualFile(getTestName(false) + ".xml"), getVirtualFile("buildserver.xml"), getVirtualFile("buildserver.properties"));
        doDoTest(true, false);
      }).assertTiming();
    }
    finally {
      myIgnoreInfos = false;
    }
  }


  @NotNull
  @Override
  protected List<HighlightInfo> doHighlighting() {
    final List<HighlightInfo> infos = super.doHighlighting();
    if (!myIgnoreInfos) {
      return infos;
    }
    return Collections.emptyList();
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new AntDuplicateTargetsInspection(),
      new AntResolveInspection(),
    new XmlPathReferenceInspection()};
  }
}
