// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInspection.unused.UnusedPropertyInspection;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;

import java.io.IOException;
import java.util.Collection;

public class PropertiesHighlightingTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/highlighting";
  }

  private void doTest(boolean checkWarnings) {
    myFixture.configureByFile(getTestName(false) + ".properties");
    ((CodeInsightTestFixtureImpl)myFixture).setVirtualFileFilter(VirtualFileFilter.NONE);
    myFixture.checkHighlighting(checkWarnings, false, false);
  }

  public void testDuplicate() { doTest(false); }

  public void testUnused() {
    myFixture.enableInspections(new UnusedPropertyInspection());
    myFixture.addClass("class C { String s = \"used.prop\"; }");
    doTest(true);
  }

  public void testPropertyUsedInLibrary() throws IOException {
    myFixture.enableInspections(new UnusedPropertyInspection());

    PsiTestUtil.removeAllRoots(getModule(), ModuleRootManager.getInstance(getModule()).getSdk());
    String libDir = myFixture.getTempDirFixture().findOrCreateDir("lib").getPath();
    PsiTestUtil.addLibrary(getModule(), "someLib", libDir, new String[]{""}, new String[]{""});
    PsiTestUtil.addSourceContentToRoots(getModule(), myFixture.getTempDirFixture().findOrCreateDir("src"));

    VirtualFile usage = myFixture.addFileToProject("lib/C.java", "class C { String s = \"used.prop\"; }").getVirtualFile();
    myFixture.addFileToProject("lib/original.properties", "used.prop=xxx");

    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(getProject());
    assertTrue(index.isInLibrarySource(usage));
    assertTrue(index.isInLibraryClasses(usage));
    assertFalse(index.isInSourceContent(usage));

    // noinspection UnusedDeclaration
    FileASTNode node = PsiManager.getInstance(getProject()).findFile(usage).getNode(); // load tree before assertions are enabled

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("Unused.properties", "src/a.properties"));
    myFixture.checkHighlighting(true, false, false);

    IProperty property = ((PropertiesFile)myFixture.getFile()).getProperties().get(1);
    assertEquals("used.prop", property.getName());
    Collection<PsiReference> references = ReferencesSearch.search(property.getPsiElement()).findAll();
    assertEquals(usage, assertOneElement(references).getElement().getContainingFile().getVirtualFile());
  }

  public void testOk() { doTest(false); }
  public void testInvalidEscape() { doTest(true); }
}
