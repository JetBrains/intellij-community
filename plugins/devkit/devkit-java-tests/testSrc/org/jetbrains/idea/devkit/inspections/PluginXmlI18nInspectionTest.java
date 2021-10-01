// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.io.File;
import java.nio.file.Paths;

@TestDataPath("$CONTENT_ROOT/testData/inspections/pluginXmlI18n")
public class PluginXmlI18nInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/pluginXmlI18n";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(LanguageExtensionPoint.class));
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP.class));
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP.class))
      .resolveSibling("intellij.platform.resources").toString());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new PluginXmlI18nInspection());
  }

  @SuppressWarnings("ComponentNotRegistered")
  public void testHighlighting() {
    setupPlatformLibraries202();
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");

    myFixture.testHighlighting("PluginXmlI18nInspection.xml");
  }

  private void setupPlatformLibraries202() {
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    File file = new File(platformApiJar);
    PsiTestUtil.addLibrary(getModule(), "idea:202.123", file.getParent(), file.getName());
  }
}
