/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.codeInspection.xml.DeprecatedClassUsageInspection
import com.intellij.diagnostic.ITNReporter
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VfsUtil
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
import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection
import org.jetbrains.idea.devkit.util.PsiUtil

@TestDataPath("\$CONTENT_ROOT/testData/codeInsight")
@CompileStatic
class PluginXmlFunctionalTest extends JavaCodeInsightFixtureTestCase {

  private TempDirTestFixture myTempDirFixture
  private PluginXmlDomInspection myInspection

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    myTempDirFixture.setUp()
    myInspection = new PluginXmlDomInspection()
    myFixture.enableInspections(myInspection)
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myTempDirFixture.tearDown()
    }
    catch (Throwable e) {
      addSuppressedException(e)
    }
    finally {
      myTempDirFixture = null
      super.tearDown()
    }
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight"
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String annotationsJar = PathUtil.getJarPathForClass(ApiStatus.class)
    moduleBuilder.addLibrary("annotations", annotationsJar)
    String pathForClass = PathUtil.getJarPathForClass(XCollection.class)
    moduleBuilder.addLibrary("util", pathForClass)
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class)
    moduleBuilder.addLibrary("platform-api", platformApiJar)
    String platformImplJar = PathUtil.getJarPathForClass(ITNReporter.class)
    moduleBuilder.addLibrary("platform-impl", platformImplJar)
    String langApiJar = PathUtil.getJarPathForClass(CompletionContributorEP.class)
    moduleBuilder.addLibrary("lang-api", langApiJar)
    String analysisApiJar = PathUtil.getJarPathForClass(LocalInspectionEP.class)
    moduleBuilder.addLibrary("analysis-api", analysisApiJar)
    String coreApiJar = PathUtil.getJarPathForClass(LanguageExtensionPoint.class) // FileTypeExtensionPoint is also there
    moduleBuilder.addLibrary("core-api", coreApiJar)
    String editorUIApi = PathUtil.getJarPathForClass(AnAction.class)
    moduleBuilder.addLibrary("editor-ui-api", editorUIApi)
    String coreImpl = PathUtil.getJarPathForClass(ServiceDescriptor.class)
    moduleBuilder.addLibrary("coreImpl", coreImpl)
  }

  void testListeners() {
    myFixture.addClass("public class MyCollectionWithoutDefaultCTOR implements java.util.Collection {" +
                       " public MyCollectionWithoutDefaultCTOR(String something) {}" +
                       "}")
    doHighlightingTest("Listeners.xml")
  }

  // absence of since-build tested only in DevKit setup, see PluginXmlPluginModuleTest
  void testListenersPre193() {
    doHighlightingTest("ListenersPre193.xml")
  }

  void testListenersOsAttributePre201() {
    doHighlightingTest("ListenersOsAttributePre201.xml")
  }

  void testListenersDepends() {
    myFixture.copyFileToProject(getTestName(false) + ".xml", "META-INF/plugin.xml")
    doHighlightingTest("ListenersDepends-dependency.xml")
  }

  void testExtensionI18n() {
    doHighlightingTest("extensionI18n.xml",
                       "extensionI18nBundle.properties", "extensionI18nAnotherBundle.properties")
  }

  void testExtensionsHighlighting() {
    final String root = "idea_core"
    addPluginXml(root, """
        <id>com.intellij</id>
        <extensionPoints>
            <extensionPoint name="completion.contributor"/>
            <extensionPoint name="myService" beanClass="foo.MyServiceDescriptor"/>
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
    myFixture.addClass("package foo; import org.jetbrains.annotations.ApiStatus.Experimental; " +
                       "@Experimental public class MyExperimentalEP { " +
                       " @com.intellij.util.xmlb.annotations.Attribute " +
                       " @Experimental public String experimentalAttribute; " +
                       "}")
    myFixture.addClass("package foo; " +
                       "import com.intellij.util.xmlb.annotations.Attribute; " +
                       "public class MyServiceDescriptor { " +
                       "  @Attribute public String serviceImplementation; " +
                       "  @Attribute public java.util.concurrent.TimeUnit timeUnit; " +
                       "  @Attribute public java.lang.Integer integerNullable; " +
                       "  @Attribute public int intPropertyForClass; " +
                       "  @Attribute public boolean forClass; " +
                       "}")

    configureByFile()
    myFixture.copyFileToProject("ExtensionsHighlighting-included.xml")
    myFixture.copyFileToProject("ExtensionsHighlighting-via-depends.xml",)
    myFixture.checkHighlighting(true, false, false)

    myFixture.testHighlighting("ExtensionsHighlighting-included.xml")
    myFixture.testHighlighting("ExtensionsHighlighting-via-depends.xml")
  }

  void testDependsHighlighting() {
    final String root = "idea_core"
    addPluginXml(root, """
        <id>com.intellij</id>
        <module value="com.intellij.modules.vcs"/>
    """)
    addPluginXml("custom", "<id>com.intellij.custom</id>")
    addPluginXml("custom2", "<id>com.intellij.custom2</id>")
    addPluginXml("custom3", "<id>com.intellij.custom3</id>")
    addPluginXml("custom4", "<id>com.intellij.custom4</id>")
    addPluginXml("duplicated", "<id>com.intellij.duplicated</id>")

    myFixture.copyFileToProject("deprecatedAttributes.xml", "META-INF/optional.xml")
    configureByFile()
    myFixture.checkHighlighting(true, false, false)
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

  void testExtensionQualifiedNameUnnecessaryDeclaration() {
    doHighlightingTest("ExtensionQualifiedNameUnnecessaryDeclaration.xml")
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

  void testInnerClassCompletionInService() {
    addPluginXml("idea_core", """
        <id>com.intellij</id>
        <extensionPoints>
            <extensionPoint name="completion.contributor"/>
            <extensionPoint name="myService" beanClass="foo.MyServiceDescriptor"/>
        </extensionPoints>
    """)
    myFixture.addClass("package foo;\n" +
                       "import com.intellij.util.xmlb.annotations.Attribute;\n" +
                       "public class MyServiceDescriptor { @Attribute public String serviceImplementation; }")
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
    ApplicationManager.application.runWriteAction { PsiTestUtil.addSourceContentToRoots(module, myTempDirFixture.getFile("")) }

    myFixture.checkHighlighting(false, false, false)
  }

  private void addPluginXml(final String root, @Language("HTML") final String text) throws IOException {
    myTempDirFixture.createFile(root + "/META-INF/plugin.xml", "<idea-plugin>$text</idea-plugin>")
    ApplicationManager.application.runWriteAction { PsiTestUtil.addSourceContentToRoots(module, myTempDirFixture.getFile(root)) }
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
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.completeBasic()
    assertSameElements(myFixture.lookupElementStrings, 'bar', 'goo')
    assertNotNull(toString(myFixture.lookup.advertisements),
                  myFixture.lookup.advertisements.find { it.contains('to see inheritors of com.intellij.openapi.actionSystem.AnAction') })
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testShowAnActionInheritorsOnSmartCompletion() {
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.addClass("package another.goo; public class AnotherAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.configureByFile(getTestName(false) + ".xml")
    myFixture.complete(CompletionType.SMART)
    assertSameElements(myFixture.lookupElementStrings, ['foo.bar.BarAction', 'foo.goo.GooAction'])
    assertNull(toString(myFixture.lookup.advertisements),
               myFixture.lookup.advertisements.find { it.contains('to see inheritors of com.intellij.openapi.actionSystem.AnAction') })
  }

  void testExtensionsSpecifyDefaultExtensionNs() {
    doHighlightingTest("extensionsSpecifyDefaultExtensionNs.xml")
  }

  void testDeprecatedExtensionAttribute() {
    myFixture.enableInspections(DeprecatedClassUsageInspection.class)
    doHighlightingTest("deprecatedExtensionAttribute.xml", "MyExtBean.java")
  }

  void testDeprecatedAttributes() {
    doHighlightingTest("deprecatedAttributes.xml")
  }

  void testExtensionAttributeDeclaredUsingAccessors() {
    doHighlightingTest("extensionAttributeWithAccessors.xml", "ExtBeanWithAccessors.java")
  }

  void testExtensionWithInnerTags() {
    doHighlightingTest("extensionWithInnerTags.xml", "ExtBeanWithInnerTags.java")
  }

  void testExtensionBeanWithDefaultValuesInAnnotations() {
    doHighlightingTest("extensionWithDefaultValuesInAnnotations.xml", "ExtBeanWithDefaultValuesInAnnotations.java")
  }

  void testExtensionDefinesWithAttributeViaAnnotation() {
    doHighlightingTest("extensionDefinesWithAttributeViaAnnotation.xml", "ExtensionDefinesWithAttributeViaAnnotation.java")
  }

  void testExtensionDefinesWithAttributeViaAnnotationCompletion() {
    myFixture.copyFileToProject("ExtensionDefinesWithAttributeViaAnnotation.java")
    myFixture.testCompletionVariants("extensionDefinesWithAttributeViaAnnotation-completion.xml",
                                     "customAttributeName", "myAttributeWithoutAnnotation")
  }

  void testExtensionDefinesWithTagViaAnnotation() {
    doHighlightingTest("extensionDefinesWithTagViaAnnotation.xml", "ExtensionDefinesWithTagViaAnnotation.java")
  }

  void testExtensionDefinesWithTagViaAnnotationCompletion() {
    myFixture.copyFileToProject("ExtensionDefinesWithTagViaAnnotation.java")
    myFixture.testCompletionVariants("extensionDefinesWithTagViaAnnotation-completion.xml",
                                     "myTag", "myTagWithoutAnnotation")
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testActionExtensionPointAttributeHighlighting() {
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")
    doHighlightingTest("actionExtensionPointAttribute.xml", "MyActionAttributeEPBean.java")
  }

  void testLanguageAttributeHighlighting() {
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.java"))
    doHighlightingTest("languageAttribute.xml", "MyLanguageAttributeEPBean.java")
  }

  void testLanguageAttributeCompletion() {
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.java"))
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguageAttributeEPBean.java"))
    myFixture.configureByFile("languageAttribute.xml")


    def lookupElements = myFixture.complete(CompletionType.BASIC).sort { it.lookupString }
    assertLookupElement(lookupElements[0], "MyAnonymousLanguageID", "MyLanguage.MySubLanguage")
    assertLookupElement(lookupElements[1], "MyLanguageID", "MyLanguage")
  }

  private static void assertLookupElement(LookupElement element, String lookupString, String typeText) {
    def presentation = new LookupElementPresentation()
    element.renderElement(presentation)
    assertEquals(lookupString, presentation.itemText)
    assertEquals(typeText, presentation.typeText)
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testIconAttribute() {
    myFixture.addClass("package foo; public class FooAction extends com.intellij.openapi.actionSystem.AnAction { }")

    myFixture.addClass("package icons; " +
                       "public class MyIcons {" +
                       "  public static final javax.swing.Icon MyCustomIcon = null; " +
                       "}")
    doHighlightingTest("iconAttribute.xml",
                       "MyIconAttributeEPBean.java")
  }

  void testPluginWithModules() {
    doHighlightingTest("pluginWithModules.xml")
  }

  void testPluginWith99InUntilBuild() {
    doHighlightingTest("pluginWith99InUntilBuild.xml")
  }

  void testPluginWith9999InUntilBuild() {
    doHighlightingTest("pluginWith9999InUntilBuild.xml")
  }

  void testPluginForOldIdeWith9999InUntilBuild() {
    doHighlightingTest("pluginForOldIdeWith9999InUntilBuild.xml")
  }

  void testPluginWith10000InUntilBuild() {
    doHighlightingTest("pluginWith10000InUntilBuild.xml")
  }

  void testPluginWithStarInUntilBuild() {
    doHighlightingTest("pluginWithStarInUntilBuild.xml")
  }

  void testPluginWithBranchNumberInUntilBuild() {
    doHighlightingTest("pluginWithBranchNumberInUntilBuild.xml")
  }

  void testPluginWithInvalidSinceUntilBuild() {
    doHighlightingTest("pluginWithInvalidSinceUntilBuild.xml")
  }

  void testReplaceBigNumberInUntilBuildWithStarQuickFix() {
    myFixture.configureByFile("pluginWithBigNumberInUntilBuild_before.xml")
    myFixture.launchAction(myFixture.findSingleIntention("Change 'until-build'"))
    myFixture.checkResultByFile("pluginWithBigNumberInUntilBuild_after.xml")
  }

  void testPluginWithXInclude() {
    myFixture.enableInspections(new XmlPathReferenceInspection())
    doHighlightingTest("pluginWithXInclude.xml", "pluginWithXInclude-extensionPoints.xml", "pluginWithXInclude-extensionPointsWithModule.xml")
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

  void testPluginXmlInIdeaProjectWithAndroidId() {
    testHighlightingInIdeaProject("pluginWithAndroidIdVendor.xml")
  }

  void testSpecifyJetBrainsAsVendorQuickFix() {
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

  void testPluginWithoutVersion() {
    doHighlightingTest("pluginWithoutVersion.xml")
    testHighlightingInIdeaProject("pluginWithoutVersion.xml")
  }

  void testOrderAttributeHighlighting() {
    doHighlightingTest("orderAttributeHighlighting.xml")
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
    PsiUtil.markAsIdeaProject(project, true)
    try {
      doHighlightingTest(path)
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
    doHighlightingTest("errorHandlerExtensionInJetBrainsPlugin.xml")
  }

  void testErrorHandlerExtensionInNonJetBrainsPlugin() {
    myFixture.addClass("""
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
public class MyErrorHandler extends ErrorReportSubmitter {}
""")
    doHighlightingTest("errorHandlerExtensionInNonJetBrainsPlugin.xml")
  }

  void testExtensionPointPresentation() {
    myFixture.configureByFile(getTestName(true) + ".xml")
    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED)
    assertNotNull(element)
    assertEquals("Extension Point", ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE))
    assertEquals("Extension Point bar", ElementDescriptionUtil.getElementDescription(element, UsageViewNodeTextLocation.INSTANCE))
  }

  void testLoadForDefaultProject() {
    configureByFile()
    myFixture.testHighlighting(true, true, true)
  }

  void testSkipForDefaultProject() {
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
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.addClass("""package foo; class PackagePrivateActionBase extends com.intellij.openapi.actionSystem.AnAction {
                                        PackagePrivateActionBase() {}
                                    } """)
    myFixture.addClass("package foo; public class ActionWithDefaultConstructor extends PackagePrivateActionBase { }")
    myFixture.addClass("package foo.bar; public class BarGroup extends com.intellij.openapi.actionSystem.ActionGroup { }")
    myFixture.addClass("package foo.bar; import org.jetbrains.annotations.NotNull;" +
                       "public class GroupWithCanBePerformed extends com.intellij.openapi.actionSystem.ActionGroup { " +
                       "    @Override " +
                       "    public boolean canBePerformed(@NotNull com.intellij.openapi.actionSystem.DataContext context) {" +
                       "    return true;" +
                       "  }" +
                       "}")
    myFixture.addFileToProject("keymaps/MyKeymap.xml", "<keymap/>")
    myFixture.testHighlighting()
  }

  void testExtensionPointNameValidity() {
    doHighlightingTest(getTestName(true) + ".xml")
  }

  void testExtensionPointValidity() {
    doHighlightingTest(getTestName(true) + ".xml")
  }

  void testRegistrationCheck() {
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.java"))
    Module anotherModule = PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, "anotherModule",
                                                 myTempDirFixture.findOrCreateDir("../anotherModuleDir"))
    ModuleRootModificationUtil.addModuleLibrary(anotherModule, VfsUtil.getUrlForLibraryRoot(new File(PathUtil.getJarPathForClass(AnAction.class))))
    Module additionalModule = PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, "additionalModule",
                                                 myTempDirFixture.findOrCreateDir("../additionalModuleDir"))
    ModuleRootModificationUtil.addModuleLibrary(anotherModule, VfsUtil.getUrlForLibraryRoot(new File(PathUtil.getJarPathForClass(LanguageExtensionPoint.class))))
    ModuleRootModificationUtil.addDependency(module, anotherModule)
    ModuleRootModificationUtil.addDependency(module, additionalModule)
    def moduleSet = new PluginXmlDomInspection.PluginModuleSet()
    moduleSet.modules.add(module.name)
    moduleSet.modules.add(additionalModule.name)
    myInspection.PLUGINS_MODULES.add(moduleSet)

    def dependencyModuleClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModuleClass.java",
                                                            "../anotherModuleDir/DependencyModuleClass.java")
    def dependencyModuleLanguageExtensionClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyLanguageExtension.java",
                                                            "../anotherModuleDir/MyLanguageExtension.java")
    def dependencyModuleLanguageExtensionPointClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyLanguageExtensionPoint.java",
                                                            "../anotherModuleDir/MyLanguageExtensionPoint.java")
    def dependencyModuleFileTypeExtensionClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyFileTypeExtension.java",
                                                            "../anotherModuleDir/MyFileTypeExtension.java")
    def dependencyModuleFileTypeExtensionPointClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/MyFileTypeExtensionPoint.java",
                                                            "../anotherModuleDir/MyFileTypeExtensionPoint.java")
    def dependencyModuleActionClass = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModuleAction.java",
                                                            "../anotherModuleDir/DependencyModuleAction.java")
    def dependencyModuleClassWithEp = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModuleClassWithEpName.java",
                                                                  "../anotherModuleDir/DependencyModuleClassWithEpName.java")
    def dependencyModulePlugin = myFixture.copyFileToProject("registrationCheck/dependencyModule/DependencyModulePlugin.xml",
                                                             "../anotherModuleDir/META-INF/DependencyModulePlugin.xml")
    def additionalModuleClass = myFixture.copyFileToProject("registrationCheck/additionalModule/AdditionalModuleClass.java",
                                                                "../additionalModuleDir/AdditionalModuleClass.java")
    def mainModuleClass = myFixture.copyFileToProject("registrationCheck/module/MainModuleClass.java",
                                                      "MainModuleClass.java")
    def mainModuleBeanClass = myFixture.copyFileToProject("registrationCheck/module/MainModuleBeanClass.java",
                                                          "MainModuleBeanClass.java")
    def mainModulePlugin = myFixture.copyFileToProject("registrationCheck/module/MainModulePlugin.xml",
                                                       "META-INF/MainModulePlugin.xml")

    myFixture.configureFromExistingVirtualFile(dependencyModuleClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleLanguageExtensionClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleLanguageExtensionPointClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleFileTypeExtensionClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleFileTypeExtensionPointClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleActionClass)
    myFixture.configureFromExistingVirtualFile(dependencyModuleClassWithEp)
    myFixture.configureFromExistingVirtualFile(dependencyModulePlugin)
    myFixture.configureFromExistingVirtualFile(additionalModuleClass)
    myFixture.configureFromExistingVirtualFile(mainModuleClass)
    myFixture.configureFromExistingVirtualFile(mainModuleBeanClass)
    myFixture.configureFromExistingVirtualFile(mainModulePlugin)

    myFixture.allowTreeAccessForAllFiles()

    myFixture.testHighlighting(true, false, false, dependencyModulePlugin)
    myFixture.testHighlighting(true, false, false, mainModulePlugin)
    def highlightInfos = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertSize(5, highlightInfos)

    for (info in highlightInfos) {
      def ranges = info.quickFixActionRanges
      assertNotNull(ranges)
      assertSize(1, ranges)
      def quickFix = ranges.get(0).getFirst().getAction()
      myFixture.launchAction(quickFix)
    }

    myFixture.checkResultByFile("../anotherModuleDir/META-INF/DependencyModulePlugin.xml",
                                "registrationCheck/dependencyModule/DependencyModulePlugin_after.xml",
                                true)
    myFixture.checkResultByFile("META-INF/MainModulePlugin.xml",
                                "registrationCheck/module/MainModulePlugin_after.xml",
                                true)
  }

  void testValuesMaxLengths() {
    doHighlightingTest("ValuesMaxLengths.xml")
  }

  void testValuesRequired() {
    doHighlightingTest("ValuesRequired.xml")
  }

  void testValuesTemplateTexts() {
    doHighlightingTest("ValuesTemplateTexts.xml")
  }

  void testPluginWithSinceBuildGreaterThanUntilBuild() {
    doHighlightingTest("pluginWithSinceBuildGreaterThanUntilBuild.xml")
  }

  private void doHighlightingTest(String... filePaths) {
    myFixture.testHighlighting(true, false, false, filePaths)
  }

  void testProductDescriptor() {
    doHighlightingTest("productDescriptor.xml")
  }

  void testProductDescriptorWithPlaceholders() {
    doHighlightingTest("productDescriptorWithPlaceholders.xml")
  }

  void testProductDescriptorInvalid() {
    doHighlightingTest("productDescriptorInvalid.xml")
  }

  void testPluginIconFound() {
    myFixture.addFileToProject("pluginIcon.svg", "fake SVG")
    myFixture.testHighlighting(true, true, true, "pluginIconFound.xml")
  }

  void testPluginIconNotNecessaryForImplementationDetail() {
    myFixture.testHighlighting(true, true, true, "pluginIconNotNecessaryForImplementationDetail.xml")
  }

  void testPluginIconNotFound() {
    myFixture.testHighlighting(true, true, true, "pluginIconNotFound.xml")
  }

  void testRedundantComponentInterfaceClass() {
    doHighlightingTest("redundantComponentInterfaceClass.xml")
  }

  void testRedundantServiceInterfaceClass() {
    doHighlightingTest("redundantServiceInterfaceClass.xml")
  }
}
