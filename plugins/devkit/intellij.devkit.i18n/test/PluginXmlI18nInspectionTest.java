// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.notification.impl.NotificationGroupEP;
import com.intellij.openapi.options.Configurable;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;

import java.io.File;
import java.nio.file.Paths;

@SuppressWarnings("InspectionDescriptionNotFoundInspection")
@TestDataPath("$CONTENT_ROOT/testData/inspections/pluginXmlI18n")
public class PluginXmlI18nInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitI18nTestUtil.TESTDATA_PATH + "inspections/pluginXmlI18n";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(LanguageExtensionPoint.class));
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP.class));
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP.class))
      .resolveSibling("intellij.platform.resources").toString());
    moduleBuilder.addLibrary("ide-core", PathUtil.getJarPathForClass(Configurable.class));
    moduleBuilder.addLibrary("ide-core-impl", PathUtil.getJarPathForClass(NotificationGroupEP.class));
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

  public void testInspectionFix() {
    setupPlatformLibraries202();
    myFixture.addClass("package foo.bar; public class Inspection1 extends com.intellij.codeInspection.LocalInspectionTool { }");
    myFixture.addFileToProject("messages/FooBundle.properties", "");

    myFixture.configureByText("plugin.xml", """
      <idea-plugin>
        <resource-bundle>messages.FooBundle</resource-bundle>  <extensions defaultExtensionNs="com.intellij">
          <localInspection hasStaticDescription="true"
                            shortName="bar"
                            displayName="Foo<caret> bar"
                            groupName="Group"
                            enabledByDefault="true"
                            implementationClass="foo.bar.Inspection1"/>
        </extension>
      </idea-plugin>""");
    IntentionAction action =
      myFixture.getAvailableIntention(DevKitI18nBundle.message("inspections.plugin.xml.i18n.inspection.tag.family.name", "displayName"));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResult("""
                            <idea-plugin>
                              <resource-bundle>messages.FooBundle</resource-bundle>  <extensions defaultExtensionNs="com.intellij">
                                <localInspection hasStaticDescription="true"
                                                 shortName="bar"
                                                 groupName="Group"
                                                 enabledByDefault="true"
                                                 implementationClass="foo.bar.Inspection1" key="inspection.bar.display.name"/>
                              </extension>
                            </idea-plugin>""");
  }

  private void setupPlatformLibraries202() {
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    File file = new File(platformApiJar);
    PsiTestUtil.addLibrary(getModule(), "idea:202.123", file.getParent(), file.getName());
  }
}
