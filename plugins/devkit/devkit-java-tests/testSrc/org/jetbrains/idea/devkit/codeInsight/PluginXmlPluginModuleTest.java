// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.PluginModuleFixture;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleFixture;
import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight")
@TestApplication
public class PluginXmlPluginModuleTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<PluginModuleFixture> pluginModule =
    pluginModuleFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = pluginModule.get().getFixture();
    myFixture.enableInspections(new PluginXmlDomInspection());
  }

  @Test
  public void testPluginWithoutVersionInPluginModule() {
    doHighlightingTest("pluginWithoutVersionInPluginModule.xml");
  }

  @Test
  public void testListeners() {
    LightJavaCodeInsightTestFixture myFixture = pluginModule.get().getFixture();
    myFixture.addClass("public class MyCollectionWithoutDefaultCTOR implements java.util.Collection {" +
                       " public MyCollectionWithoutDefaultCTOR(String something) {}" +
                       "}");
    doHighlightingTest("Listeners.xml");
  }

  @Test
  public void testListenersNoSinceBuild() {
    doHighlightingTest("ListenersNoSinceBuild.xml");
  }

  @Test
  public void testListenersNoPluginIdStandalone() {
    doHighlightingTest("ListenersNoPluginIdStandalone.xml");
  }

  @Test
  public void testListenersPre193() {
    doHighlightingTest("ListenersPre193.xml");
  }

  @Test
  public void testListenersOsAttributePre201() {
    doHighlightingTest("ListenersOsAttributePre201.xml");
  }

  @Test
  public void testListenersDepends() {
    pluginModule.get().setPluginXml("ListenersDepends.xml");
    doHighlightingTest("ListenersDepends-dependency.xml");
  }

  private void doHighlightingTest(@TestDataFile String path) {
    LightJavaCodeInsightTestFixture myFixture = pluginModule.get().getFixture();
    myFixture.testHighlighting(false, false, false, path);
  }
}
