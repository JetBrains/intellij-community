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
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/contentDependencyDescriptor")
public class PluginXmlContentDependencyDescriptorTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight/contentDependencyDescriptor";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
    String editorUIApi = PathUtil.getJarPathForClass(AnAction.class);
    moduleBuilder.addLibrary("editor-ui-api", editorUIApi);
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

    List<String> lookupElements = myFixture.getCompletionVariants("META-INF/plugin.xml");
    assertSameElements(lookupElements, "anotherModule");

    final LookupElementPresentation anotherModule = getLookupElementPresentation("anotherModule");
    assertEquals(AllIcons.Nodes.Module, anotherModule.getIcon());
    //noinspection SpellCheckingInspection
    assertEquals("mypackage.subpackage", anotherModule.getTypeText());
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

  private void doHighlightingTest() {
    doHighlightingTest("anotherModule");
  }

  private void doHighlightingTest(String... dependencyModuleNames) {
    setupModules(dependencyModuleNames);

    myFixture.enableInspections(new PluginXmlDomInspection());
    myFixture.testHighlighting(true, false, false, "META-INF/plugin.xml");
  }

  private void setupModules(String... dependencyModuleNames) {
    String testName = getTestName(true);
    myFixture.copyDirectoryToProject("/" + testName + "/mainModule", "/");

    if (dependencyModuleNames.length == 0) return;

    for (String moduleName : dependencyModuleNames) {
      final VirtualFile dependencyModuleRoot =
        myFixture.copyDirectoryToProject("/" + testName + "/" + moduleName, "/" + moduleName);

      Module dependencyModule = PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, moduleName, dependencyModuleRoot);
      ModuleRootModificationUtil.addDependency(getModule(), dependencyModule);
    }
  }

  private LookupElementPresentation getLookupElementPresentation(String lookupString) {
    final LookupElement lookupElement = ContainerUtil.find(myFixture.getLookupElements(),
                                                           element -> element.getLookupString().equals(lookupString));
    assertNotNull(lookupString, lookupElement);
    return LookupElementPresentation.renderElement(lookupElement);
  }
}
