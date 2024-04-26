// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.nio.file.Paths;

@TestDataPath("$CONTENT_ROOT/testData/inspections/pluginXmlExtensionRegistration")
public class PluginXmlExtensionRegistrationInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/pluginXmlExtensionRegistration";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("platform-core", PathUtil.getJarPathForClass(Project.class));
    moduleBuilder.addLibrary("ide-core", PathUtil.getJarPathForClass(ConfigurableEP.class));
    moduleBuilder.addLibrary("platform-core-impl", PathUtil.getJarPathForClass(PsiReferenceContributorEP.class));
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(IntentionActionBean.class));
    moduleBuilder.addLibrary("platform-util-base", PathUtil.getJarPathForClass(IncorrectOperationException.class));
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP.class))
      .resolveSibling("intellij.platform.resources").toString());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PluginXmlExtensionRegistrationInspection.class, XmlUnresolvedReferenceInspection.class);
  }

  public void testStatusBarWidgetFactoryEP() {
    myFixture.testHighlighting("statusBarWidgetFactory.xml");
  }

  public void testConfigurableEP() {
    myFixture.testHighlighting("configurableEP.xml", "bundle.properties");
  }

  public void testLanguageExtensions() {
    myFixture.testHighlighting("languageExtensions.xml");
  }

  public void testLanguageAddLanguageTagFix() {
    IntentionAction action =
      myFixture.getAvailableIntention("Add language tag",
                                      "languageAddLanguageTagFix.xml");
    assertNotNull(action);
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResultByFile("languageAddLanguageTagFix_after.xml");
  }

  public void testLanguageAddLanguageAttributeForCompletionContributorEPFix() {
    IntentionAction action =
      myFixture.getAvailableIntention("Define language attribute",
                                      "addLanguageAttributeForCompletionContributorEPFix.xml");
    assertNotNull(action);
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResultByFile("addLanguageAttributeForCompletionContributorEPFix_after.xml");
  }

  public void testStubElementTypeHolder() {
    myFixture.testHighlighting("stubElementTypeHolder.xml");
  }

  public void testInspectionMappings() {
    myFixture.testHighlighting("inspectionMapping.xml", "bundle.properties");
  }

  public void testInspectionMappingsWithDefaultBundle() {
    myFixture.testHighlighting("inspectionMappingWithDefaultBundle.xml", "bundle.properties");
  }

  public void testRedundantServiceInterfaceClass() {
    myFixture.testHighlighting("redundantServiceInterfaceClass.xml");
  }
}
