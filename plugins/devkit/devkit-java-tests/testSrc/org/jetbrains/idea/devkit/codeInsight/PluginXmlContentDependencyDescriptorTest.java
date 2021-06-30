// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/contentDependencyDescriptor")
public class PluginXmlContentDependencyDescriptorTest extends JavaCodeInsightFixtureTestCase {

  @NonNls
  private static final String MAIN_MODULE_NAME = "mainModule";

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight/contentDependencyDescriptor";
  }

  public void testNonJetBrainsHighlighting() {
    doHighlightingTest(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void testEmptyContentDependenciesHighlighting() {
    doHighlightingTest(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void testDependencyDescriptorHighlighting() {
    doHighlightingTest();
  }

  public void testContentDescriptorModuleNameCompletion() {
    setupModules("anotherModule");

    myFixture.configureFromTempProjectFile(MAIN_MODULE_NAME + "/META-INF/plugin.xml");
    myFixture.completeBasic();
    List<String> lookupElements = myFixture.getLookupElementStrings();
    assertSameElements(lookupElements, "anotherModule", "anotherModule/secondary-descriptor");

    final LookupElementPresentation anotherModule = getLookupElementPresentation("anotherModule");
    assertEquals(AllIcons.Nodes.Module, anotherModule.getIcon());
    assertFalse(anotherModule.isItemTextBold());
    //noinspection SpellCheckingInspection
    assertEquals("mypackage.subpackage", anotherModule.getTypeText());

    final LookupElementPresentation anotherModuleSecondary = getLookupElementPresentation("anotherModule/secondary-descriptor");
    assertTrue(anotherModuleSecondary.isItemTextBold());
  }

  public void testContentDescriptorHighlighting() {
    doHighlightingTest();
  }

  public void testContentDescriptorDuplicatedHighlighting() {
    doHighlightingTest();
  }

  public void testContentDescriptorNoModuleDescriptorFileHighlighting() {
    doHighlightingTest();
  }

  public void testContentDescriptorPackageMismatchHighlighting() {
    doHighlightingTest();
  }

  public void testContentDescriptorNoPackageInMainHighlighting() {
    doHighlightingTest();
  }

  private void doHighlightingTest() {
    doHighlightingTest("anotherModule");
  }

  private void doHighlightingTest(String... dependencyModuleNames) {
    setupModules(dependencyModuleNames);

    myFixture.enableInspections(new PluginXmlDomInspection());
    myFixture.testHighlighting(true, false, false, "mainModule/META-INF/plugin.xml");
  }

  private void setupModules(String... dependencyModuleNames) {

    final Module mainModule = addModule(MAIN_MODULE_NAME);
    for (String moduleName : dependencyModuleNames) {
      final Module dependencyModule = addModule(moduleName);
      ModuleRootModificationUtil.addDependency(mainModule, dependencyModule);
    }
  }

  private Module addModule(String moduleName) {
    String testName = getTestName(true);

    final VirtualFile dependencyModuleRoot =
      myFixture.copyDirectoryToProject("/" + testName + "/" + moduleName, "/" + moduleName);

    Module dependencyModule = PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, moduleName, dependencyModuleRoot);
    PsiTestUtil.addLibrary(dependencyModule, "editor-ui-api", PathUtil.getJarPathForClass(AnAction.class));

    return dependencyModule;
  }

  private LookupElementPresentation getLookupElementPresentation(String lookupString) {
    final LookupElement lookupElement = ContainerUtil.find(myFixture.getLookupElements(),
                                                           element -> element.getLookupString().equals(lookupString));
    assertNotNull(lookupString, lookupElement);
    return LookupElementPresentation.renderElement(lookupElement);
  }
}
