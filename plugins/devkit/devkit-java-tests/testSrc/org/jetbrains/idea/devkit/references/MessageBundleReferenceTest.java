// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.lang.properties.codeInspection.unused.UnusedPropertyInspection;
import com.intellij.openapi.components.State;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/references/messageBundle")
public class MessageBundleReferenceTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("platform-projectModel", PathUtil.getJarPathForClass(State.class));
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "references/messageBundle";
  }

  public void testIconTooltipKeyImplicitUsage() {
    doHighlightImplicitUsagesTest();
  }

  public void testPluginDescriptionKeyImplicitUsage() {
    doHighlightImplicitUsagesTest("PluginDescriptionKeyImplicitUsage.xml");
  }

  public void testActionOrGroupImplicitUsage() {
    doHighlightImplicitUsagesTest("ActionOrGroupImplicitUsage.xml");
  }

  public void testToolWindowIdImplicitUsage() {
    doHighlightImplicitUsagesTest("ToolWindowIdImplicitUsage.xml");
  }

  public void testExportableIdImplicitUsage() {
    doHighlightImplicitUsagesTest("ExportableIdImplicitUsage.java");
  }

  public void testAdvancedSettingImplicitUsage() {
    doHighlightImplicitUsagesTest("AdvancedSettingImplicitUsage.xml");
  }

  public void testNoImplicitUsageWrongFilename() {
    myFixture.enableInspections(new UnusedPropertyInspection());
    myFixture.testHighlighting("NoImplicitUsageWrongFilename.properties");
  }

  private void doHighlightImplicitUsagesTest(String... additionalFiles) {
    final String testName = getTestName(false);

    myFixture.enableInspections(new UnusedPropertyInspection());
    myFixture.testHighlighting(ArrayUtil.prepend(testName + "Bundle.properties", additionalFiles));
  }

  public void testToolWindowIdCompletionVariants() {
    myFixture.copyFileToProject("ToolWindowIdImplicitUsage.xml");
    myFixture.configureByText("MyBundle.properties", "toolwindow.stripe.<caret>");

    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "myToolWindowId", "myToolWindowId_With_Spaces");
  }

  public void testExportableIdCompletionVariants() {
    myFixture.copyFileToProject("ExportableIdImplicitUsage.java");
    myFixture.configureByText("MyBundle.properties", "exportable.<caret>.presentable.name");

    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "MyStateName");
  }

  public void testPluginIdCompletionVariants() {
    myFixture.copyFileToProject("PluginDescriptionKeyImplicitUsage.xml");
    myFixture.configureByText("MyBundle.properties", "plugin.<caret>.description");

    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "my.plugin.id");
  }

  public void testAdvancedSettingIdCompletionVariants() {
    myFixture.copyFileToProject("AdvancedSettingImplicitUsage.xml");
    myFixture.configureByText("MyBundle.properties", "advanced.setting.<caret>");

    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "my.advanced.setting.id", "my.another.advanced.setting.id");
  }
}