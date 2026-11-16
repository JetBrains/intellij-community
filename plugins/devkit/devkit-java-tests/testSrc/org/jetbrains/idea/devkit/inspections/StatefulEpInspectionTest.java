// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;
import static org.jetbrains.idea.devkit.inspections.StatefulEpFixtureKt.statefulEpFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/statefulEp")
@TestApplication
public class StatefulEpInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<PluginModuleFixture> statefulEp =
    statefulEpFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/statefulEp");

  @Test
  public void testFix() {
    statefulEp.get().doTest();
  }

  @Test
  public void testNonFix() {
    statefulEp.get().setPluginXml("plugin.xml");
    statefulEp.get().doTest();
  }

  @Test
  public void testExt() {
    statefulEp.get().setPluginXml("plugin.xml");
    statefulEp.get().doTest();
  }
  
  @Test
  public void testProjectComp() {
    statefulEp.get().setPluginXml("plugin.xml");
    statefulEp.get().doTest();
  }
  
  @Test
  public void testProjectService() {
    statefulEp.get().setPluginXml("plugin.xml");
    statefulEp.get().doTest();
  }

  @Test
  public void testProjectConfigurable() {
    statefulEp.get().setPluginXml("plugin.xml");
    statefulEp.get().doTest();
  }

  @Test
  public void testFakeFile() {
    statefulEp.get().setPluginXml("plugin.xml");
    statefulEp.get().doTest();
  }

  @Test
  public void testPackagedExt() {
    statefulEp.get().setPluginXml("packagedPlugin.xml");
    statefulEp.get().doTest();
  }

  @Test
  public void testCapturedFromOuterClass() {
    statefulEp.get().doTest();
  }

  @Test
  public void testCollectionTests() {
    statefulEp.get().doTest();
  }

  @Test
  public void testMapTests() {
    statefulEp.get().doTest();
  }

  @Test
  public void testRefTests() {
    statefulEp.get().doTest();
  }
}
