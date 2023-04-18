// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.inspections.StatefulEpInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/statefulEp")
public class KtStatefulEpInspectionTest extends StatefulEpInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/statefulEp";
  }

  @Override
  protected String getFileExtension() {
    return "kt";
  }

  public void testFix() {
    doTest();
  }

  public void testNonFix() {
    setPluginXml("plugin.xml");
    doTest();
  }

  public void testExt() {
    setPluginXml("plugin.xml");
    doTest();
  }

  public void testProjectComp() {
    setPluginXml("plugin.xml");
    doTest();
  }

  public void testProjectService() {
    setPluginXml("plugin.xml");
    doTest();
  }

  public void testProjectConfigurable() {
    setPluginXml("plugin.xml");
    doTest();
  }

  public void testFakeFile() {
    setPluginXml("plugin.xml");
    doTest();
  }

  public void testCapturedFromOuterClass() {
    doTest();
  }

  public void testCollectionTests() {
    doTest();
  }

  public void testMapTests() {
    doTest();
  }

  public void testRefTests() {
    doTest();
  }
}
