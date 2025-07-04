// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.xml.DeprecatedClassUsageInspection;
import com.intellij.diagnostic.ITNReporter;
import com.intellij.icons.AllIcons;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.notification.impl.NotificationGroupEP;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.ui.components.JBList;
import com.intellij.usageView.UsageViewNodeTextLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection;

import java.io.IOException;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight")
public class PluginXmlFunctionalTest extends JavaCodeInsightFixtureTestCase {

  private TempDirTestFixture myTempDirFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myTempDirFixture.setUp();
    myFixture.enableInspections(new PluginXmlDomInspection(), new XmlUnresolvedReferenceInspection());
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myTempDirFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myTempDirFixture = null;
      super.tearDown();
    }
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String annotationsJar = PathUtil.getJarPathForClass(ApiStatus.class);
    moduleBuilder.addLibrary("annotations", annotationsJar);
    String platformUtilJar = PathUtil.getJarPathForClass(XCollection.class);
    moduleBuilder.addLibrary("platform-util", platformUtilJar);
    String platformIdeJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-ide", platformIdeJar);
    String platformIdeImplJar = PathUtil.getJarPathForClass(ITNReporter.class);
    moduleBuilder.addLibrary("platform-ide-impl", platformIdeImplJar);
    String platformIdeCore = PathUtil.getJarPathForClass(Configurable.class);
    moduleBuilder.addLibrary("platform-ide-core", platformIdeCore);
    String platformIdeCoreImpl = PathUtil.getJarPathForClass(NotificationGroupEP.class);
    moduleBuilder.addLibrary("platform-ide-core-impl", platformIdeCoreImpl);
    String platformAnalysisJar = PathUtil.getJarPathForClass(LocalInspectionEP.class);
    moduleBuilder.addLibrary("platform-analysis", platformAnalysisJar);
    String platformCore = PathUtil.getJarPathForClass(LanguageExtensionPoint.class);
    moduleBuilder.addLibrary("platform-core", platformCore);
    String platformEditorJar = PathUtil.getJarPathForClass(AnAction.class);
    moduleBuilder.addLibrary("platform-editor", platformEditorJar);
    String platformUiUtilJar = PathUtil.getJarPathForClass(AllIcons.class);
    moduleBuilder.addLibrary("platform-util-ui", platformUiUtilJar);
    String langApiJar = PathUtil.getJarPathForClass(InlayHintsProviderExtensionBean.class);
    moduleBuilder.addLibrary("lang-api", langApiJar);
  }

  // Gradle-like setup, but JBList not in Library
  public void testListenerUnresolvedTargetPlatform() {
    doHighlightingTest("ListenersUnresolvedTargetPlatform.xml");
  }

  public void testExtensionI18n() {
    doHighlightingTest("extensionI18n.xml",
                       "MyBundle.properties", "AnotherBundle.properties");
  }

  public void testMessageBundleViaIncluding() {
    myFixture.copyFileToProject("messageBundleIncluding.xml", "META-INF/messageBundleIncluding.xml");
    myFixture.copyFileToProject("messageBundleViaIncluding.xml", "META-INF/messageBundleViaIncluding.xml");
    doHighlightingTest("META-INF/messageBundleViaIncluding.xml",
                       "MyBundle.properties");
  }

  public void testExtensionsHighlighting() {
    final String root = "idea_core";
    addPluginXml(root, """
          <id>com.intellij</id>
          <extensionPoints>
              <extensionPoint name="completion.contributor"/>
              <extensionPoint name="myService" beanClass="foo.MyServiceDescriptor"/>
          </extensionPoints>
      """);
    addPluginXml("indirect", """
          <id>com.intellij.indirect</id>
          <extensionPoints>
              <extensionPoint name="indirect"/>
          </extensionPoints>
      """);
    addPluginXml("custom", """
          <id>com.intellij.custom</id>
          <depends>com.intellij.indirect</depends>
          <extensionPoints>
              <extensionPoint name="custom"/>
          </extensionPoints>
      """);
    addPluginXml("notInDependencies", """
          <id>com.intellij.notincluded</id>
          <extensionPoints>
              <extensionPoint name="notInDependencies"/>
          </extensionPoints>
      """);
    myFixture.addClass("package foo; public class MyRunnable implements java.lang.Runnable {}");
    myFixture.addClass("package foo; @Deprecated public abstract class MyDeprecatedEP {}");
    myFixture.addClass("package foo; public class MyDeprecatedEPImpl extends foo.MyDeprecatedEP {}");
    myFixture.addClass("package foo; @Deprecated(forRemoval=true) public interface MyDeprecatedForRemovalEP {}");
    myFixture.addClass("package foo; public class MyDeprecatedForRemovalEPImpl implements MyDeprecatedForRemovalEP {}");

    myFixture.addClass("""
                         package foo;
                         import org.jetbrains.annotations.ApiStatus.Experimental;
                         
                         @Experimental
                         public class MyExperimentalEP {
                          @com.intellij.util.xmlb.annotations.Attribute
                          @Experimental
                          public String experimentalAttribute;
                         }""");
    //noinspection RedundantScheduledForRemovalAnnotation,SSBasedInspection
    myFixture.addClass("""
                         package foo;
                         import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
                         
                         @ScheduledForRemoval(inVersion = "removeVersion")
                         @Deprecated
                         public class MyScheduledForRemovalEP {
                          @com.intellij.util.xmlb.annotations.Attribute
                          @Deprecated
                          @ScheduledForRemoval
                          public String scheduledForRemovalAttribute;
                         }
                         """);
    myFixture.addClass("""
                         package foo;
                         import org.jetbrains.annotations.ApiStatus.Internal;
                         
                         @Internal
                         public class MyInternalEP {
                          @com.intellij.util.xmlb.annotations.Attribute
                          @Internal public String internalAttribute;
                         }
                         """);
    myFixture.addClass("""
                         package foo;
                         import org.jetbrains.annotations.ApiStatus.Obsolete;
                         
                         @Obsolete
                         public class MyObsoleteEP {
                          @com.intellij.util.xmlb.annotations.Attribute
                          @Obsolete public String obsoleteAttribute;
                         }
                         """);
    myFixture.addClass("""
                         package foo;
                         import com.intellij.util.xmlb.annotations.Attribute;
                         import com.intellij.util.xmlb.annotations.Property;
                         
                         @Property(style = Property.Style.ATTRIBUTE)
                         public class ClassLevelProperty {
                          public String classLevel;
                          @Attribute("customAttributeName") public int intProperty;
                         }
                         """);
    myFixture.addClass("""
                         package foo;
                         import com.intellij.util.xmlb.annotations.Attribute;
                         import com.intellij.openapi.extensions.RequiredElement;
                         
                         public class MyServiceDescriptor {
                           @Attribute public String serviceImplementation;
                           @Attribute public java.util.concurrent.TimeUnit timeUnit;
                           @Attribute public java.lang.Integer integerNullable;
                           @Attribute public int intPropertyForClass;
                           @Attribute @RequiredElement(allowEmpty=true) public String canBeEmptyString;
                           @Attribute public boolean forClass;
                           @Attribute("class") public boolean _class;
                           @Attribute("myClassName") public boolean className;
                         }
                         """);

    configureByFile();
    myFixture.copyFileToProject("ExtensionsHighlighting-included.xml");
    myFixture.copyFileToProject("ExtensionsHighlighting-via-depends.xml");
    myFixture.checkHighlighting(true, false, false);

    doHighlightingTest("ExtensionsHighlighting-included.xml");
    doHighlightingTest("ExtensionsHighlighting-via-depends.xml");
  }

  public void testExtensionsDependencies() {
    addExtensionsModule("ExtensionsDependencies-module");

    VirtualFile contentFile = addExtensionsModule("ExtensionsDependencies-content");
    VirtualFile contentSubDescriptorFile =
      myFixture.copyFileToProject("ExtensionsDependencies-content.subDescriptor.xml",
                                  "/ExtensionsDependencies-content/ExtensionsDependencies-content.subDescriptor.xml");

    myFixture.addFileToProject("dummy-descriptor.xml", "<idea-plugin></idea-plugin>");
    doHighlightingTest("ExtensionsDependencies.xml",
                       "ExtensionsDependencies-plugin.xml");

    myFixture.configureFromExistingVirtualFile(contentFile);
    doHighlightingTest();

    myFixture.configureFromExistingVirtualFile(contentSubDescriptorFile);
    doHighlightingTest();
  }

  private VirtualFile addExtensionsModule(String name) {
    try {
      String moduleDescriptorFilename = name + ".xml";
      VirtualFile moduleRoot = myFixture.getTempDirFixture().findOrCreateDir(name);
      VirtualFile file = myFixture.copyFileToProject(moduleDescriptorFilename, "/" + name + "/" + moduleDescriptorFilename);
      Module dependencyModule = PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), name, moduleRoot);
      ModuleRootModificationUtil.setModuleSdk(dependencyModule, IdeaTestUtil.getMockJdk17());
      ModuleRootModificationUtil.addDependency(getModule(), dependencyModule);
      return file;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testDependsHighlighting() {
    final String root = "idea_core";
    addPluginXml(root, """
          <id>com.intellij</id>
          <module value="com.intellij.modules.vcs"/>
      """);
    addPluginXml("custom", "<id>com.intellij.custom</id>");
    addPluginXml("custom2", "<id>com.intellij.custom2</id>");
    addPluginXml("custom3", "<id>com.intellij.custom3</id>");
    addPluginXml("custom4", "<id>com.intellij.custom4</id>");
    addPluginXml("duplicated", "<id>com.intellij.duplicated</id>");

    myFixture.copyFileToProject("deprecatedAttributes.xml", "META-INF/optional.xml");
    configureByFile();
    myFixture.checkHighlighting(true, false, false);
  }

  public void testDependsConfigFileCompletion() {
    myFixture.copyFileToProject("ExtensionsHighlighting.xml", "META-INF/used.xml");
    myFixture.copyFileToProject("ExtensionsHighlighting.xml", "META-INF/optional.xml");
    myFixture.copyFileToProject("ExtensionsHighlighting.xml", "META-INF/optional2.xml");
    configureByFile();

    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "optional.xml", "optional2.xml");
  }

  public void testDependsCompletion() {
    addPluginXml("platform", """
          <id>com.intellij</id>
          <module value="com.intellij.modules.vcs"/>
      """);
    addPluginXml("lang", """
          <id>com.intellij</id>
          <module value="com.intellij.modules.lang"/>
          <module value="com.intellij.modules.lang.another"/>
      """);
    addPluginXml("custom", "<id>com.intellij.custom</id>");
    configureByFile();

    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(),
                           "com.intellij.modules.vcs",
                           "com.intellij.modules.lang", "com.intellij.modules.lang.another",
                           "com.intellij.custom",
                           "com.intellij.modules.os.mac", "com.intellij.modules.os.windows");
  }

  private void configureByFile() {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".xml", "META-INF/plugin.xml"));
  }

  public void testExtensionQualifiedName() {
    myFixture.addClass("package foo; public class MyRunnable implements java.lang.Runnable {}");
    configureByFile();
    myFixture.checkHighlighting(false, false, false);
  }

  public void testExtensionQualifiedNameUnnecessaryDeclaration() {
    doHighlightingTest("ExtensionQualifiedNameUnnecessaryDeclaration.xml");
  }

  public void testInnerClassReferenceHighlighting() {
    myFixture.addClass("package foo; public class Foo { public static class Fubar {} }");
    myFixture.testHighlighting("innerClassReferenceHighlighting.xml");
  }

  public void testInnerClassCompletion() {
    myFixture.addClass("package foo; public class Foo { public static class Fubar {} }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testInnerClassCompletionInService() {
    addPluginXml("idea_core", """
          <id>com.intellij</id>
          <extensionPoints>
              <extensionPoint name="completion.contributor"/>
              <extensionPoint name="myService" beanClass="foo.MyServiceDescriptor"/>
          </extensionPoints>
      """);
    myFixture.addClass("""
                         package foo;
                         import com.intellij.util.xmlb.annotations.Attribute;
                         public class MyServiceDescriptor { @Attribute public String serviceImplementation; }""");
    myFixture.addClass("package foo; public class Foo { public static class Fubar {} }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testInnerClassSmartCompletion() {
    myFixture.addClass("package foo; public class Foo { public static class Fubar extends Foo {} }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testResolveExtensionsFromDependentDescriptor() {
    addPluginXml("xxx", """
          <id>com.intellij.xxx</id>
          <extensionPoints>
              <extensionPoint name="completion.contributor"/>
          </extensionPoints>
      """);

    myFixture.copyFileToProject(getTestName(false) + "_main.xml", "META-INF/plugin.xml");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + "_dependent.xml", "META-INF/dep.xml"));
    ApplicationManager.getApplication().runWriteAction(
      (Computable<SourceFolder>)() -> PsiTestUtil.addSourceContentToRoots(getModule(), myTempDirFixture.getFile("")));

    myFixture.checkHighlighting(false, false, false);
  }

  private void addPluginXml(final String root, @Language("HTML") final String text) {
    try {
      myTempDirFixture.createFile(root + "/META-INF/plugin.xml", "<idea-plugin>" + text + "</idea-plugin>");
      ApplicationManager.getApplication().runWriteAction((ThrowableComputable<SourceFolder, RuntimeException>)() ->
        PsiTestUtil.addSourceContentToRoots(getModule(), myTempDirFixture.getFile(root)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testNoWordCompletionInClassPlaces() {
    myFixture.addClass("package foo; public class FooFooFooFooFoo { }");
    myFixture.addClass("package foo; public interface ExtIntf { }");

    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    myFixture.type('\'');
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testNoClassCompletionOutsideJavaReferences() {
    myFixture.addClass("package foo; public class FooFooFooFooFoo { }");

    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testShowPackagesInActionClass() {
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "bar", "goo");
    assertNotNull(toString(myFixture.getLookup().getAdvertisements()),
                  ContainerUtil.find(myFixture.getLookup().getAdvertisements(),
                                     it -> it.contains("to see inheritors of com.intellij.openapi.actionSystem.AnAction")));
  }

  public void testShowAnActionInheritorsOnSmartCompletion() {
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.addClass("package another.goo; public class AnotherAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.complete(CompletionType.SMART);
    assertSameElements(myFixture.getLookupElementStrings(), List.of("foo.bar.BarAction", "foo.goo.GooAction"));
    assertNull(toString(myFixture.getLookup().getAdvertisements()),
               ContainerUtil.find(myFixture.getLookup().getAdvertisements(),
                                  it -> it.contains("to see inheritors of com.intellij.openapi.actionSystem.AnAction")));
  }

  public void testExtensionsSpecifyDefaultExtensionNs() {
    doHighlightingTest("extensionsSpecifyDefaultExtensionNs.xml");
  }

  public void testDeprecatedExtensionAttribute() {
    myFixture.enableInspections(DeprecatedClassUsageInspection.class);
    doHighlightingTest("deprecatedExtensionAttribute.xml", "MyExtBean.java");
  }

  public void testDeprecatedAttributes() {
    doHighlightingTest("deprecatedAttributes.xml");
  }

  public void testExtensionAttributeDeclaredUsingAccessors() {
    doHighlightingTest("extensionAttributeWithAccessors.xml", "ExtBeanWithAccessors.java");
  }

  public void testExtensionWithInnerTags() {
    doHighlightingTest("extensionWithInnerTags.xml", "ExtBeanWithInnerTags.java");
  }

  public void testExtensionBeanWithDefaultValuesInAnnotations() {
    doHighlightingTest("extensionWithDefaultValuesInAnnotations.xml", "ExtBeanWithDefaultValuesInAnnotations.java");
  }

  public void testExtensionDefinesWithAttributeViaAnnotation() {
    doHighlightingTest("extensionDefinesWithAttributeViaAnnotation.xml", "ExtensionDefinesWithAttributeViaAnnotation.java");
  }

  public void testExtensionDefinesWithAttributeViaAnnotationCompletion() {
    myFixture.copyFileToProject("ExtensionDefinesWithAttributeViaAnnotation.java");
    myFixture.testCompletionVariants("extensionDefinesWithAttributeViaAnnotation-completion.xml",
                                     "customAttributeName", "myAttributeWithoutAnnotation");
  }

  public void testExtensionDefinesWithTagViaAnnotation() {
    doHighlightingTest("extensionDefinesWithTagViaAnnotation.xml", "ExtensionDefinesWithTagViaAnnotation.java");
  }

  public void testExtensionDefinesWithTagViaAnnotationCompletion() {
    myFixture.copyFileToProject("ExtensionDefinesWithTagViaAnnotation.java");
    myFixture.testCompletionVariants("extensionDefinesWithTagViaAnnotation-completion.xml",
                                     "myTag", "myTagWithoutAnnotation");
  }

  public void testActionExtensionPointAttributeHighlighting() {
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");
    doHighlightingTest("actionExtensionPointAttribute.xml", "MyActionAttributeEPBean.java");
  }

  public void testLanguageAttributeHighlighting() {
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.java"));
    doHighlightingTest("languageAttribute.xml", "MyLanguageAttributeEPBean.java");
  }

  public void testLanguageAttributeCompletion() {
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.java"));
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguageAttributeEPBean.java"));
    myFixture.configureByFile("languageAttribute.xml");


    LookupElement[] lookupElements = myFixture.complete(CompletionType.BASIC);
    assertLookupElement(lookupElements, "MyAnonymousLanguageID", null, "MyLanguage.MySubLanguage");
    assertLookupElement(lookupElements, "MyLanguageID", null, "MyLanguage");
  }

  public void testIconAttribute() {
    myFixture.addClass("package foo; public class FooAction extends com.intellij.openapi.actionSystem.AnAction { }");

    myFixture.enableInspections(DeprecatedClassUsageInspection.class);
    addIconClasses();
    doHighlightingTest("iconAttribute.xml",
                       "MyIconAttributeEPBean.java");
  }

  public void testIconAttributeCompletion() {
    addIconClasses();
    myFixture.configureByFile("iconAttributeCompletion.xml");
    Registry.get("ide.completion.variant.limit").setValue("5000", getTestRootDisposable());
    myFixture.completeBasic();

    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertContainsElements(lookupElementStrings,
                           "AllIcons.Providers.Mysql",
                           "MyIcons.MyCustomIcon",
                           "my.FqnIcons.MyFqnIcon", "my.FqnIcons.Inner.MyInnerFqnIcon");
  }

  private void addIconClasses() {
    myFixture.addClass("""
                         package icons;
                         public final class MyIcons {
                           public static final javax.swing.Icon MyCustomIcon = null;
                         }
                         """);
    myFixture.addClass("""
                         package my;
                         public final class FqnIcons {
                           public static final javax.swing.Icon MyFqnIcon = null;
                           public static final class Inner {
                             public static final javax.swing.Icon MyInnerFqnIcon = null;
                           }
                         }
                         """);
  }

  public void testPluginWithModules() {
    doHighlightingTest("pluginWithModules.xml");
  }

  public void testPluginAttributes() {
    myFixture.addFileToProject("com/intellij/package-info.java",
                               "package com.intellij;");
    doHighlightingTest("pluginAttributes.xml");
  }

  public void testPluginWith99InUntilBuild() {
    doHighlightingTest("pluginWith99InUntilBuild.xml");
  }

  public void testPluginWith9999InUntilBuild() {
    doHighlightingTest("pluginWith9999InUntilBuild.xml");
  }

  public void testPluginForOldIdeWith9999InUntilBuild() {
    doHighlightingTest("pluginForOldIdeWith9999InUntilBuild.xml");
  }

  public void testPluginWith10000InUntilBuild() {
    doHighlightingTest("pluginWith10000InUntilBuild.xml");
  }

  public void testPluginWithStarInUntilBuild() {
    doHighlightingTest("pluginWithStarInUntilBuild.xml");
  }

  public void testPluginWithBranchNumberInUntilBuild() {
    doHighlightingTest("pluginWithBranchNumberInUntilBuild.xml");
  }

  public void testPluginWithInvalidSinceUntilBuild() {
    doHighlightingTest("pluginWithInvalidSinceUntilBuild.xml");
  }

  public void testReplaceBigNumberInUntilBuildWithStarQuickFix() {
    myFixture.configureByFile("pluginWithBigNumberInUntilBuild_before.xml");
    myFixture.launchAction(myFixture.findSingleIntention("Change 'until-build'"));
    myFixture.checkResultByFile("pluginWithBigNumberInUntilBuild_after.xml");
  }

  public void testPluginWithXInclude() {
    myFixture.copyFileToProject("pluginWithXInclude.xml", "META-INF/pluginWithXInclude.xml");
    myFixture.copyFileToProject("pluginWithXInclude-extensionPoints.xml", "META-INF/pluginWithXInclude-extensionPoints.xml");
    myFixture.copyFileToProject("pluginWithXInclude-extensionPointsWithModule.xml",
                                "META-INF/pluginWithXInclude-extensionPointsWithModule.xml");
    myFixture.enableInspections(new XmlPathReferenceInspection());
    doHighlightingTest("META-INF/pluginWithXInclude.xml");
  }

  public void testPluginXmlInIdeaProjectWithoutVendor() {
    testHighlightingInIdeaProject("pluginWithoutVendor.xml");
  }

  public void testPluginXmlInIdeaProjectWithThirdPartyVendor() {
    testHighlightingInIdeaProject("pluginWithThirdPartyVendor.xml");
  }

  public void testPluginWithJetBrainsAsVendor() {
    testHighlightingInIdeaProject("pluginWithJetBrainsAsVendor.xml");
  }

  public void testPluginWithJetBrainsAndMeAsVendor() {
    testHighlightingInIdeaProject("pluginWithJetBrainsAndMeAsVendor.xml");
  }

  public void testPluginXmlInIdeaProjectWithAndroidId() {
    testHighlightingInIdeaProject("pluginWithAndroidIdVendor.xml");
  }

  public void testSpecifyJetBrainsAsVendorQuickFix() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(getProject(), true);
    try {
      myFixture.configureByFile("pluginWithoutVendor_before.xml");
      IntentionAction fix = myFixture.findSingleIntention("Specify JetBrains");
      myFixture.launchAction(fix);
      myFixture.checkResultByFile("pluginWithoutVendor_after.xml");
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(getProject(), false);
    }
  }

  public void testPluginWithoutVersion() {
    doHighlightingTest("pluginWithoutVersion.xml");
    testHighlightingInIdeaProject("pluginWithoutVersion.xml");
  }

  public void testOrderAttributeHighlighting() {
    doHighlightingTest("orderAttributeHighlighting.xml");
  }

  // separate tests for 'order' attribute completion because cannot test all cases with completeBasicAllCarets

  public void testOrderAttributeCompletionKeywordsInEmptyValue() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml",
                                     LoadingOrder.FIRST_STR, LoadingOrder.LAST_STR,
                                     LoadingOrder.BEFORE_STR.trim(), LoadingOrder.AFTER_STR.trim());
  }

  public void testOrderAttributeCompletionBeforeKeyword() {
    myFixture.testCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testOrderAttributeCompletionKeywords() {
    // no first/last because there's already 'first'
    myFixture.testCompletionVariants(getTestName(true) + ".xml",
                                     LoadingOrder.BEFORE_STR.trim(), LoadingOrder.AFTER_STR.trim());
  }

  public void testOrderAttributeCompletionIds() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "id1", "id2", "id3");
  }

  public void testOrderAttributeCompletionIdsWithFirst() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "id1", "id2", "id3");
  }

  public void testOrderAttributeCompletionFirstKeywordWithId() {
    myFixture.testCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testOrderAttributeCompletionLanguage() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "anyLanguage", "javaLanguage1", "withoutLanguage");
  }

  public void testOrderAttributeCompletionFileType() {
    myFixture.testCompletionVariants(getTestName(true) + ".xml", "javaFiletype1", "withoutFiletype");
  }


  private void testHighlightingInIdeaProject(String path) {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(getProject(), true);
    try {
      doHighlightingTest(path);
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(getProject(), false);
    }
  }

  public void testErrorHandlerExtensionInJetBrainsPlugin() {
    myFixture.addClass("""
                         import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
                         public class MyErrorHandler extends ErrorReportSubmitter {}
                         """);
    doHighlightingTest("errorHandlerExtensionInJetBrainsPlugin.xml");
  }

  public void testErrorHandlerExtensionInNonJetBrainsPlugin() {
    myFixture.addClass("""
                         import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
                         public class MyErrorHandler extends ErrorReportSubmitter {}
                         """);
    doHighlightingTest("errorHandlerExtensionInNonJetBrainsPlugin.xml");
  }

  public void testExtensionPointPresentation() {
    myFixture.configureByFile(getTestName(true) + ".xml");
    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    assertEquals("Extension Point", ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE));
    assertEquals("Extension Point bar", ElementDescriptionUtil.getElementDescription(element, UsageViewNodeTextLocation.INSTANCE));
  }

  public void testLoadForDefaultProject() {
    configureByFile();
    myFixture.testHighlighting(true, true, true);
  }

  public void testSkipForDefaultProject() {
    configureByFile();
    myFixture.testHighlighting(true, true, true);
  }

  public void testCreateRequiredAttribute() {
    myFixture.configureByFile(getTestName(true) + ".xml");
    myFixture.launchAction(myFixture.findSingleIntention("Define class attribute"));
    myFixture.checkResultByFile(getTestName(true) + "_after.xml");
  }

  public void testActionCompletion() {
    configureByFile();
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.copyFileToProject("ActionCompletionBundle.properties");

    myFixture.completeBasic();
    LookupElement[] lookupElements = myFixture.getLookupElements();
    assertLookupElement(lookupElements, "actionId", " \"ActionId Text\"", "ActionId description");
    assertLookupElement(lookupElements, "actionId.localized", " \"Action Localized Text\"", "Action localized description");
    assertLookupElement(lookupElements, "BarAction", " \"Action without ID Localized Text\"", "Action without ID localized description");
    assertLookupElement(lookupElements, "actionId.missing.localized", null, null);
  }

  public void testActionOverrideTextPlaceCompletion() {
    configureByFile();
    myFixture.addClass("public interface CustomPlaces { String CUSTOM=\"custom\"; }");

    myFixture.completeBasic();
    LookupElement[] lookupElements = myFixture.getLookupElements();
    assertLookupElement(lookupElements, "FavoritesPopup", " (FAVORITES_VIEW_POPUP)", "ActionPlaces");
    assertLookupElement(lookupElements, "custom", " (CUSTOM)", "CustomPlaces");
  }

  public void testActionOverrideTextPlaceResolve() {
    configureByFile();

    PsiReference reference = myFixture.getReferenceAtCaretPositionWithAssertion();
    PsiField resolvedField = assertInstanceOf(reference.resolve(), PsiField.class);
    assertEquals("FAVORITES_VIEW_POPUP", resolvedField.getName());
  }

  public void testExtensionPointNameValidity() {
    doHighlightingTestWithWeakWarnings(getTestName(true) + ".xml");
  }

  public void testExtensionPointValidity() {
    doHighlightingTest(getTestName(true) + ".xml");
  }

  public void testValuesMaxLengths() {
    doHighlightingTest("ValuesMaxLengths.xml");
  }

  public void testValuesRequiredOrInvalid() {
    doHighlightingTest("ValuesRequiredOrInvalid.xml");
  }

  public void testValuesTemplateTexts() {
    doHighlightingTest("ValuesTemplateTexts.xml");
  }

  public void testPluginWithSinceBuildGreaterThanUntilBuild() {
    doHighlightingTest("pluginWithSinceBuildGreaterThanUntilBuild.xml");
  }

  public void testProductDescriptor() {
    doHighlightingTest("productDescriptor.xml");
  }

  public void testProductDescriptorWithPlaceholders() {
    doHighlightingTest("productDescriptorWithPlaceholders.xml");
  }

  public void testProductDescriptorInvalid() {
    doHighlightingTest("productDescriptorInvalid.xml");
  }

  public void testRedundantComponentInterfaceClass() {
    doHighlightingTest("redundantComponentInterfaceClass.xml");
  }

  public void testDeprecatedImplementationDetailAttribute() {
    doHighlightingTest("deprecatedImplementationDetail.xml");
  }

  private void doHighlightingTest(String... filePaths) {
    myFixture.testHighlighting(true, false, false, filePaths);
  }

  private void doHighlightingTestWithWeakWarnings(String... filePaths) {
    myFixture.testHighlighting(true, false, true, filePaths);
  }

  private static void assertLookupElement(LookupElement[] variants, String lookupText, String tailText, String typeText) {
    LookupElement lookupElement = ContainerUtil.find(variants, it -> lookupText.equals(it.getLookupString()));
    assertNotNull(toString(variants, "\n"), lookupElement);

    LookupElementPresentation presentation = new LookupElementPresentation();
    lookupElement.renderElement(presentation);

    assertEquals(lookupText, presentation.getItemText());
    assertEquals(tailText, presentation.getTailText());
    assertEquals(typeText, presentation.getTypeText());
  }
}
