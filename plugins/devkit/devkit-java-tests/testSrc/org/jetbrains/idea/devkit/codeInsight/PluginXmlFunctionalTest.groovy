// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.codeInspection.xml.DeprecatedClassUsageInspection
import com.intellij.diagnostic.ITNReporter
import com.intellij.icons.AllIcons
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.impl.NotificationGroupEP
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReference
import com.intellij.testFramework.IdeaTestUtil
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
    String platformUtilJar = PathUtil.getJarPathForClass(XCollection.class)
    moduleBuilder.addLibrary("platform-util", platformUtilJar)
    String platformIdeJar = PathUtil.getJarPathForClass(JBList.class)
    moduleBuilder.addLibrary("platform-ide", platformIdeJar)
    String platformIdeImplJar = PathUtil.getJarPathForClass(ITNReporter.class)
    moduleBuilder.addLibrary("platform-ide-impl", platformIdeImplJar)
    String platformIdeCore = PathUtil.getJarPathForClass(Configurable.class)
    moduleBuilder.addLibrary("platform-ide-core", platformIdeCore)
    String platformIdeCoreImpl = PathUtil.getJarPathForClass(NotificationGroupEP.class)
    moduleBuilder.addLibrary("platform-ide-core-impl", platformIdeCoreImpl)
    String platformAnalysisJar = PathUtil.getJarPathForClass(LocalInspectionEP.class)
    moduleBuilder.addLibrary("platform-analysis", platformAnalysisJar)
    String platformCore = PathUtil.getJarPathForClass(LanguageExtensionPoint.class)
    moduleBuilder.addLibrary("platform-core", platformCore)
    String platformEditorJar = PathUtil.getJarPathForClass(AnAction.class)
    moduleBuilder.addLibrary("platform-editor", platformEditorJar)
    String platformUiUtilJar = PathUtil.getJarPathForClass(AllIcons.class)
    moduleBuilder.addLibrary("platform-util-ui", platformUiUtilJar)
  }

  // Gradle-like setup, but JBList not in Library
  void testListenerUnresolvedTargetPlatform() {
    doHighlightingTest("ListenersUnresolvedTargetPlatform.xml")
  }

  void testExtensionI18n() {
    doHighlightingTest("extensionI18n.xml",
            "MyBundle.properties", "AnotherBundle.properties")
  }

  void testMessageBundleViaIncluding() {
    myFixture.copyFileToProject("messageBundleIncluding.xml", "META-INF/messageBundleIncluding.xml")
    myFixture.copyFileToProject("messageBundleViaIncluding.xml", "META-INF/messageBundleViaIncluding.xml")
    doHighlightingTest("META-INF/messageBundleViaIncluding.xml",
                       "MyBundle.properties")
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
    addPluginXml("notInDependencies", """
        <id>com.intellij.notincluded</id>
        <extensionPoints>
            <extensionPoint name="notInDependencies"/>
        </extensionPoints>
    """)
    myFixture.addClass("package foo; public class MyRunnable implements java.lang.Runnable {}")
    myFixture.addClass("package foo; @Deprecated public abstract class MyDeprecatedEP {}")
    myFixture.addClass("package foo; public class MyDeprecatedEPImpl extends foo.MyDeprecatedEP {}")
    myFixture.addClass("package foo; @Deprecated(forRemoval=true) public interface MyDeprecatedForRemovalEP {}")
    myFixture.addClass("package foo; public class MyDeprecatedForRemovalEPImpl implements MyDeprecatedForRemovalEP {}")

    myFixture.addClass("package foo; import org.jetbrains.annotations.ApiStatus.Experimental; " +
                       "@Experimental public class MyExperimentalEP { " +
                       " @com.intellij.util.xmlb.annotations.Attribute " +
                       " @Experimental public String experimentalAttribute; " +
                       "}")
    myFixture.addClass("package foo; import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval; " +
                       "@ScheduledForRemoval(inVersion = \"removeVersion\") @Deprecated public class MyScheduledForRemovalEP {}")
    myFixture.addClass("package foo; import org.jetbrains.annotations.ApiStatus.Internal; " +
                       "@Internal public class MyInternalEP {" +
                       " @com.intellij.util.xmlb.annotations.Attribute " +
                       " @Internal public String internalAttribute; " +
                       "}")
    myFixture.addClass("package foo; import org.jetbrains.annotations.ApiStatus.Obsolete; " +
                       "@Obsolete public class MyObsoleteEP {" +
                       " @com.intellij.util.xmlb.annotations.Attribute " +
                       " @Obsolete public String obsoleteAttribute; " +
                       "}")
    myFixture.addClass("package foo; " +
                       "import com.intellij.util.xmlb.annotations.Attribute; " +
                       "import com.intellij.util.xmlb.annotations.Property; " +
                       "@Property(style = Property.Style.ATTRIBUTE) " +
                       "public class ClassLevelProperty {" +
                       " public String classLevel;"+
                       " @Attribute(\"customAttributeName\") public int intProperty; " +
                       "}")
    myFixture.addClass("package foo; " +
                       "import com.intellij.util.xmlb.annotations.Attribute; " +
                       "import com.intellij.openapi.extensions.RequiredElement; " +
                       "public class MyServiceDescriptor { " +
                       "  @Attribute public String serviceImplementation; " +
                       "  @Attribute public java.util.concurrent.TimeUnit timeUnit; " +
                       "  @Attribute public java.lang.Integer integerNullable; " +
                       "  @Attribute public int intPropertyForClass; " +
                       "  @Attribute @RequiredElement(allowEmpty=true) public String canBeEmptyString; " +
                       "  @Attribute public boolean forClass; " +
                       "  @Attribute(\"class\") public boolean _class; " +
                       "  @Attribute(\"myClassName\") public boolean className; " +
                       "}")

    configureByFile()
    myFixture.copyFileToProject("ExtensionsHighlighting-included.xml")
    myFixture.copyFileToProject("ExtensionsHighlighting-via-depends.xml",)
    myFixture.checkHighlighting(true, false, false)

    myFixture.testHighlighting("ExtensionsHighlighting-included.xml")
    myFixture.testHighlighting("ExtensionsHighlighting-via-depends.xml")
  }

  void testExtensionsDependencies() {
    addExtensionsModule("ExtensionsDependencies-module")

    VirtualFile contentFile = addExtensionsModule("ExtensionsDependencies-content")
    VirtualFile contentSubDescriptorFile =
      myFixture.copyFileToProject("ExtensionsDependencies-content.subDescriptor.xml",
                                  "/ExtensionsDependencies-content/ExtensionsDependencies-content.subDescriptor.xml")

    myFixture.addFileToProject("dummy-descriptor.xml","<idea-plugin></idea-plugin>")
    doHighlightingTest("ExtensionsDependencies.xml",
                       "ExtensionsDependencies-plugin.xml")

    myFixture.configureFromExistingVirtualFile(contentFile)
    doHighlightingTest()

    myFixture.configureFromExistingVirtualFile(contentSubDescriptorFile)
    doHighlightingTest()
  }

  private VirtualFile addExtensionsModule(String name) {
    String moduleDescriptorFilename = name+ ".xml"
    VirtualFile moduleRoot = myFixture.tempDirFixture.findOrCreateDir(name)
    VirtualFile file = myFixture.copyFileToProject(moduleDescriptorFilename, "/" + name + "/" + moduleDescriptorFilename)
    Module dependencyModule = PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, name, moduleRoot)
    ModuleRootModificationUtil.setModuleSdk(dependencyModule, IdeaTestUtil.getMockJdk17())
    ModuleRootModificationUtil.addDependency(getModule(), dependencyModule)
    return file
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


    def lookupElements = myFixture.complete(CompletionType.BASIC)
    assertLookupElement(lookupElements, "MyAnonymousLanguageID", null, "MyLanguage.MySubLanguage")
    assertLookupElement(lookupElements, "MyLanguageID", null, "MyLanguage")
  }

  @SuppressWarnings("ComponentNotRegistered")
  void testIconAttribute() {
    myFixture.addClass("package foo; public class FooAction extends com.intellij.openapi.actionSystem.AnAction { }")

    myFixture.enableInspections(DeprecatedClassUsageInspection.class)
    addIconClasses()
    doHighlightingTest("iconAttribute.xml",
                       "MyIconAttributeEPBean.java")
  }

  void testIconAttributeCompletion() {
    addIconClasses()
    myFixture.configureByFile("iconAttributeCompletion.xml")
    Registry.get("ide.completion.variant.limit").setValue("5000", getTestRootDisposable())
    myFixture.completeBasic()

    List<String> lookupElementStrings = myFixture.getLookupElementStrings()
    assertContainsElements(lookupElementStrings,
                           "AllIcons.Providers.Mysql",
                           "MyIcons.MyCustomIcon",
                           "my.FqnIcons.MyFqnIcon", "my.FqnIcons.Inner.MyInnerFqnIcon")
  }

  private void addIconClasses() {
    myFixture.addClass("package icons; " +
                       "public class MyIcons {" +
                       "  public static final javax.swing.Icon MyCustomIcon = null; " +
                       "}")
    myFixture.addClass("package my; " +
                       "public class FqnIcons {" +
                       "  public static final javax.swing.Icon MyFqnIcon = null; " +
                       "  " +
                       "  public static class Inner {" +
                       "    public static final javax.swing.Icon MyInnerFqnIcon = null; " +
                       "  }" +
                       "}")
  }

  void testPluginWithModules() {
    doHighlightingTest("pluginWithModules.xml")
  }

  void testPluginAttributes() {
    myFixture.addFileToProject("com/intellij/package-info.java",
                               "package com.intellij;")
    myFixture.testHighlighting(true,
                               true,
                               true,
                               "pluginAttributes.xml")
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
    myFixture.copyFileToProject("pluginWithXInclude.xml", "META-INF/pluginWithXInclude.xml")
    myFixture.copyFileToProject("pluginWithXInclude-extensionPoints.xml", "META-INF/pluginWithXInclude-extensionPoints.xml")
    myFixture.copyFileToProject("pluginWithXInclude-extensionPointsWithModule.xml", "META-INF/pluginWithXInclude-extensionPointsWithModule.xml")
    myFixture.enableInspections(new XmlPathReferenceInspection())
    doHighlightingTest("META-INF/pluginWithXInclude.xml")
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
  void testActionCompletion() {
    configureByFile()
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }")
    myFixture.copyFileToProject("ActionCompletionBundle.properties")

    myFixture.completeBasic()
    LookupElement[] lookupElements = myFixture.getLookupElements()
    assertLookupElement(lookupElements, "actionId", " \"ActionId Text\"", "ActionId description")
    assertLookupElement(lookupElements, "actionId.localized", " \"Action Localized Text\"", "Action localized description")
    assertLookupElement(lookupElements, "actionId.missing.localized", null, null)
  }

  void testActionOverrideTextPlaceCompletion() {
    configureByFile()
    myFixture.addClass("public interface CustomPlaces { String CUSTOM=\"custom\"; }")

    myFixture.completeBasic()
    LookupElement[] lookupElements = myFixture.getLookupElements()
    assertLookupElement(lookupElements, "FavoritesPopup", " (FAVORITES_VIEW_POPUP)", "ActionPlaces")
    assertLookupElement(lookupElements, "custom", " (CUSTOM)", "CustomPlaces")
  }

  void testActionOverrideTextPlaceResolve() {
    configureByFile()

    PsiReference reference = myFixture.getReferenceAtCaretPositionWithAssertion()
    PsiField resolvedField = assertInstanceOf(reference.resolve(), PsiField.class)
    assertEquals("FAVORITES_VIEW_POPUP", resolvedField.getName())
  }

  void testExtensionPointNameValidity() {
    doHighlightingTestWithWeakWarnings(getTestName(true) + ".xml")
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

      def ranges = actions(info)
      assertSize(1, ranges)
      def quickFix = ranges.get(0)
      myFixture.launchAction(quickFix)
    }

    myFixture.checkResultByFile("../anotherModuleDir/META-INF/DependencyModulePlugin.xml",
                                "registrationCheck/dependencyModule/DependencyModulePlugin_after.xml",
                                true)
    myFixture.checkResultByFile("META-INF/MainModulePlugin.xml",
                                "registrationCheck/module/MainModulePlugin_after.xml",
                                true)
  }
  static List<IntentionAction> actions(HighlightInfo info) {
    List<IntentionAction> result = new ArrayList<IntentionAction>()
    info.findRegisteredQuickFix((descriptor,range) -> {
      result.add(descriptor.getAction())
      return null
    })
    return result
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

  private void doHighlightingTest(String... filePaths) {
    myFixture.testHighlighting(true, false, false, filePaths)
  }

  private void doHighlightingTestWithWeakWarnings(String... filePaths) {
    myFixture.testHighlighting(true, false, true, filePaths)
  }

  private static void assertLookupElement(LookupElement[] variants, String lookupText, String tailText, String typeText) {
    LookupElement lookupElement = variants.find { it.lookupString == lookupText }
    assertNotNull(toString(variants, "\n"), lookupElement)

    def presentation = new LookupElementPresentation()
    lookupElement.renderElement(presentation)

    assertEquals(lookupText, presentation.itemText)
    assertEquals(tailText, presentation.tailText)
    assertEquals(typeText, presentation.typeText)
  }
}
