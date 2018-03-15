/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.xml.DeprecatedClassUsageInspection
import com.intellij.diagnostic.ITNReporter
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.ui.components.JBList
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.XCollection
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection
import org.jetbrains.idea.devkit.util.PsiUtil

@TestDataPath("\$CONTENT_ROOT/testData/codeInsight")
class PluginXmlFunctionalTest extends JavaCodeInsightFixtureTestCase {

  private TempDirTestFixture myTempDirFixture

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    myFixture.enableInspections(new PluginXmlDomInspection())
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/codeInsight"
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String pathForClass = PathUtil.getJarPathForClass(XCollection.class)
    moduleBuilder.addLibrary("util", pathForClass)
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class)
    moduleBuilder.addLibrary("platform-api", platformApiJar)
    String platformImplJar = PathUtil.getJarPathForClass(ITNReporter.class)
    moduleBuilder.addLibrary("platform-impl", platformImplJar)
    String langApiJar = PathUtil.getJarPathForClass(CompletionContributorEP.class)
    moduleBuilder.addLibrary("lang-api", langApiJar)
    String coreApiJar = PathUtil.getJarPathForClass(LanguageExtensionPoint.class) // FileTypeExtensionPoint is also there
    moduleBuilder.addLibrary("core-api", coreApiJar)
  }

  void testExtensionsHighlighting() {
    final String root = "idea_core"
    addPluginXml(root, """
        <id>com.intellij</id>
        <extensionPoints>
            <extensionPoint name="completion.contributor"/>
        </extensionPoints>
    """)
    addPluginXml("indirect", """
        <id>com.intellij.indirect</id>
        <extensionPoints>
            <extensionPoint name="indirect"/>
        </extensionPoints>
    """)
    addPluginXml("custom", """
        <id>com.intellij.custom</id>
        <depends>com.intellij.indirect</depends>
        <extensionPoints>
            <extensionPoint name="custom"/>
        </extensionPoints>
    """)
    myFixture.addClass("package foo; public class MyRunnable implements java.lang.Runnable {}")
    myFixture.addClass("package foo; @Deprecated public abstract class MyDeprecatedEP {}")
    myFixture.addClass("package foo; public class MyDeprecatedEPImpl extends foo.MyDeprecatedEP {}")

    configureByFile()
    myFixture.checkHighlighting(true, false, false)
  }

  void testDependsHighlighting() {
    final String root = "idea_core"
    addPluginXml(root, """
        <id>com.intellij</id>
        <module value="com.intellij.modules.vcs"/>
    """)
    addPluginXml("custom", "<id>com.intellij.custom</id>")

    myFixture.copyFileToProject("deprecatedAttributes.xml", "META-INF/optional.xml")
    configureByFile()
    myFixture.checkHighlighting(false, false, false)
  }

  void testDependsConfigFileCompletion() {
    myFixture.copyFileToProject("ExtensionsHighlighting.xml", "META-INF/used.xml")
    myFixture.copyFileToProject("ExtensionsHighlighting.xml", "META-INF/optional.xml")
    myFixture.copyFileToProject("ExtensionsHighlighting.xml", "META-INF/optional2.xml")
    configureByFile()

    myFixture.completeBasic()
    assertSameElements(myFixture.getLookupElementStrings(), "optional.xml", "optional2.xml")
  }

  void testDependsCompletion() {
    addPluginXml("platform", """
        <id>com.intellij</id>
        <module value="com.intellij.modules.vcs"/>
    """)
    addPluginXml("lang", """
        <id>com.intellij</id>
        <module value="com.intellij.modules.lang"/>
        <module value="com.intellij.modules.lang.another"/>
    """)
    addPluginXml("custom", "<id>com.intellij.custom</id>")
    configureByFile()

    myFixture.completeBasic()
    assertSameElements(myFixture.lookupElementStrings,
                       'com.intellij.modules.vcs',
                       'com.intellij.modules.lang', 'com.intellij.modules.lang.another',
                       'com.intellij.custom')
  }

  private void configureByFile() {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".xml", "META-INF/plugin.xml"))
  }

  void testExtensionQualifiedName() {
    myFixture.addClass("package foo; public class MyRunnable implements java.lang.Runnable {}")
    configureByFile()
    myFixture.checkHighlighting(false, false, false)
  }

  void testInnerClassReferenceHighlighting() {
    myFixture.addClass("package foo; public class Foo { public static class Fubar {} }")
    myFixture.testHighlighting("innerClassReferenceHighlighting.xml")
  }

  void testInnerClassCompletion() {
    myFixture.addClass("package foo; public class Foo { public static class Fubar {} }")
    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResultByFile(getTestName(false) + "_after.xml")
  }

  void testInnerClassSmartCompletion() {
    myFixture.addClass("package foo; public class Foo { public static class Fubar extends Foo {} }")
    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.complete(CompletionType.SMART)
    myFixture.checkResultByFile(getTestName(false) + "_after.xml")
  }

  void testResolveExtensionsFromDependentDescriptor() {
    addPluginXml("xxx", """
        <id>com.intellij.xxx</id>
        <extensionPoints>
            <extensionPoint name="completion.contributor"/>
        </extensionPoints>
    """)

    myFixture.copyFileToProject(getTestName(false) + "_main.xml", "META-INF/plugin.xml")
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + "_dependent.xml", "META-INF/dep.xml"))
    ApplicationManager.application.runWriteAction { PsiTestUtil.addSourceContentToRoots(myModule, myTempDirFixture.getFile("")) }

    myFixture.checkHighlighting(false, false, false)
  }

  private void addPluginXml(final String root, @Language("HTML") final String text) throws IOException {
    myTempDirFixture.createFile(root + "/META-INF/plugin.xml", "<idea-plugin>$text</idea-plugin>")
    ApplicationManager.application.runWriteAction { PsiTestUtil.addSourceContentToRoots(myModule, myTempDirFixture.getFile(root)) }
  }

  void testNoWordCompletionInClassPlaces() {
    myFixture.addClass("package foo; public class FooFooFooFooFoo { }")
    myFixture.addClass("package foo; public interface ExtIntf { }")

    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.completeBasic()
    myFixture.type('\'')
    myFixture.checkResultByFile(getTestName(false) + "_after.xml")
  }

  void testNoClassCompletionOutsideJavaReferences() {
    myFixture.addClass("package foo; public class FooFooFooFooFoo { }")

    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.completeBasic()
    myFixture.checkResultByFile(getTestName(false) + "_after.xml")
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testShowPackagesInActionClass() {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction { }")
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['bar', 'goo']
    assert myFixture.lookup.advertisements.find { it.contains('to see inheritors of com.intellij.openapi.actionSystem.AnAction') }
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testShowAnActionInheritorsOnSmartCompletion() {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction { }")
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.addClass("package another.goo; public class AnotherAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.complete(CompletionType.SMART)
    assert myFixture.lookupElementStrings == ['foo.bar.BarAction', 'foo.goo.GooAction']
    assert !myFixture.lookup.advertisements.find { it.contains('to see inheritors of com.intellij.openapi.actionSystem.AnAction') }
  }

  void testExtensionsSpecifyDefaultExtensionNs() {
    myFixture.testHighlighting("extensionsSpecifyDefaultExtensionNs.xml")
  }

  void testDeprecatedExtensionAttribute() {
    myFixture.enableInspections(DeprecatedClassUsageInspection.class)
    myFixture.testHighlighting("deprecatedExtensionAttribute.xml", "MyExtBean.java")
  }

  void testDeprecatedAttributes() {
    myFixture.testHighlighting("deprecatedAttributes.xml")
  }

  void testExtensionAttributeDeclaredUsingAccessors() {
    myFixture.testHighlighting("extensionAttributeWithAccessors.xml", "ExtBeanWithAccessors.java")
  }

  void testExtensionWithInnerTags() {
    myFixture.testHighlighting("extensionWithInnerTags.xml", "ExtBeanWithInnerTags.java")
  }

  void testLanguageAttributeHighlighting() {
    configureLanguageAttributeTest()
    myFixture.testHighlighting("languageAttribute.xml", "MyLanguageAttributeEPBean.java")
  }

  void testLanguageAttributeCompletion() {
    configureLanguageAttributeTest()
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguageAttributeEPBean.java"))
    myFixture.configureByFile("languageAttribute.xml")


    def lookupElements = myFixture.complete(CompletionType.BASIC).sort { it.lookupString }
    assertLookupElement(lookupElements[0], "MyAnonymousLanguageID", "MyLanguage.MySubLanguage")
    assertLookupElement(lookupElements[1], "MyLanguageID", "MyLanguage")
  }

  private static void assertLookupElement(LookupElement element, String lookupString, String typeText) {
    def presentation = new LookupElementPresentation()
    element.renderElement(presentation)
    assert presentation.itemText == lookupString
    assert presentation.typeText == typeText
  }

  private void configureLanguageAttributeTest() {
    myFixture.addClass("package com.intellij.lang; " +
                       "public class Language { " +
                       "  protected Language(String id) {}" +
                       "}")
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.java"))
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testIconAttribute() {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction { }")
    myFixture.addClass("package foo; public class FooAction extends com.intellij.openapi.actionSystem.AnAction { }")

    myFixture.addClass("package icons; " +
                       "public class MyIcons {" +
                       "  public static final javax.swing.Icon MyCustomIcon = null; " +
                       "}")
    myFixture.testHighlighting("iconAttribute.xml",
                               "MyIconAttributeEPBean.java")
  }

  void testPluginWithModules() {
    myFixture.testHighlighting("pluginWithModules.xml")
  }

  void testPluginWith99InUntilBuild() {
    myFixture.testHighlighting("pluginWith99InUntilBuild.xml")
  }

  void testPluginWith9999InUntilBuild() {
    myFixture.testHighlighting("pluginWith9999InUntilBuild.xml")
  }

  void testPluginForOldIdeWith9999InUntilBuild() {
    myFixture.testHighlighting("pluginForOldIdeWith9999InUntilBuild.xml")
  }

  void testPluginWith10000InUntilBuild() {
    myFixture.testHighlighting("pluginWith10000InUntilBuild.xml")
  }

  void testPluginWithStarInUntilBuild() {
    myFixture.testHighlighting("pluginWithStarInUntilBuild.xml")
  }

  void testPluginWithBranchNumberInUntilBuild() {
    myFixture.testHighlighting("pluginWithBranchNumberInUntilBuild.xml")
  }

  void testReplaceBigNumberInUntilBuildWithStarQuickFix() {
    myFixture.enableInspections(PluginXmlDomInspection.class)
    myFixture.configureByFile("pluginWithBigNumberInUntilBuild_before.xml")
    myFixture.launchAction(myFixture.findSingleIntention("Change 'until-build'"))
    myFixture.checkResultByFile("pluginWithBigNumberInUntilBuild_after.xml")
  }

  void testPluginWithXInclude() {
    myFixture.testHighlighting("pluginWithXInclude.xml", "pluginWithXInclude-extensionPoints.xml")
  }

  void testPluginXmlInIdeaProjectWithoutVendor() {
    testHighlightingInIdeaProject("pluginWithoutVendor.xml")
  }

  void testPluginXmlInIdeaProjectWithThirdPartyVendor() {
    testHighlightingInIdeaProject("pluginWithThirdPartyVendor.xml")
  }

  void testPluginWithJetBrainsAsVendor() {
    testHighlightingInIdeaProject("pluginWithJetBrainsAsVendor.xml")
  }

  void testPluginWithJetBrainsAndMeAsVendor() {
    testHighlightingInIdeaProject("pluginWithJetBrainsAndMeAsVendor.xml")
  }

  void testSpecifyJetBrainsAsVendorQuickFix() {
    myFixture.enableInspections(PluginXmlDomInspection.class)
    PsiUtil.markAsIdeaProject(project, true)
    try {
      myFixture.configureByFile("pluginWithoutVendor_before.xml")
      def fix = myFixture.findSingleIntention("Specify JetBrains")
      myFixture.launchAction(fix)
      myFixture.checkResultByFile("pluginWithoutVendor_after.xml")
    }
    finally {
      PsiUtil.markAsIdeaProject(project, false)
    }
  }

  void testOrderAttributeHighlighting() {
    myFixture.testHighlighting("orderAttributeHighlighting.xml")
  }

  // separate tests for 'order' attribute completion because cannot test all cases with completeBasicAllCarets

  void testOrderAttributeCompletionKeywordsInEmptyValue() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml",
                                     LoadingOrder.FIRST_STR, LoadingOrder.LAST_STR,
                                     LoadingOrder.BEFORE_STR.trim(), LoadingOrder.AFTER_STR.trim())
  }

  void testOrderAttributeCompletionBeforeKeyword() {
    myFixture.testCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  void testOrderAttributeCompletionKeywords() {
    // no first/last because there's already 'first'
    myFixture.testCompletionVariants(getTestName(true) + ".xml",
                                     LoadingOrder.BEFORE_STR.trim(), LoadingOrder.AFTER_STR.trim())
  }

  void testOrderAttributeCompletionIds() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "id1", "id2", "id3")
  }

  void testOrderAttributeCompletionIdsWithFirst() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "id1", "id2", "id3")
  }

  void testOrderAttributeCompletionFirstKeywordWithId() {
    myFixture.testCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  void testOrderAttributeCompletionLanguage() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "anyLanguage", "javaLanguage1", "withoutLanguage")
  }

  void testOrderAttributeCompletionFileType() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "javaFiletype1", "withoutFiletype")
  }


  private void testHighlightingInIdeaProject(String path) {
    myFixture.enableInspections(PluginXmlDomInspection.class)
    PsiUtil.markAsIdeaProject(project, true)
    try {
      myFixture.testHighlighting(path)
    }
    finally {
      PsiUtil.markAsIdeaProject(project, false)
    }
  }

  void testErrorHandlerExtensionInJetBrainsPlugin() {
    myFixture.addClass("""
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
public class MyErrorHandler extends ErrorReportSubmitter {}
""")
    myFixture.testHighlighting("errorHandlerExtensionInJetBrainsPlugin.xml")
  }

  void testExtensionPointPresentation() {
    myFixture.configureByFile(getTestName(true) + ".xml")
    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED)
    assert element != null
    assertEquals("Extension Point", ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE))
    assertEquals("Extension Point bar", ElementDescriptionUtil.getElementDescription(element, UsageViewNodeTextLocation.INSTANCE))
  }

  void testLoadForDefaultProject() throws Exception {
    configureByFile()
    myFixture.testHighlighting(true, true, true)
  }

  void testSkipForDefaultProject() throws Exception {
    configureByFile()
    myFixture.testHighlighting(true, true, true)
  }

  void testCreateRequiredAttribute() {
    myFixture.configureByFile(getTestName(true) + ".xml")
    myFixture.launchAction(myFixture.findSingleIntention("Define class attribute"))
    myFixture.checkResultByFile(getTestName(true) + "_after.xml")
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testActionHighlighting() {
    configureByFile()
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction { }")
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")

    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup { }")
    myFixture.addClass("package foo.bar; public class BarGroup extends com.intellij.openapi.actionSystem.ActionGroup { }")
    myFixture.testHighlighting()
  }

  void testExtensionPointNameValidity() {
    myFixture.testHighlighting(getTestName(true) + ".xml")
  }
}
