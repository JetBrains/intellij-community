// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtilRt;

public class JUnitDependencyScopeSuggesterTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    //moduleBuilder.addLibrary("junit3", JavaSdkUtil.getJunit3JarPath());
    moduleBuilder.addLibrary("junit4", ArrayUtilRt.toStringArray(JavaSdkUtil.getJUnit4JarPaths()));
    moduleBuilder.addLibrary("ideaRt", JavaSdkUtil.getIdeaRtJarPath());
    moduleBuilder.addLibrary("empty");
  }

  public void _testJunit3() {
    assertSame(DependencyScope.TEST, getScope("junit3"));
  }

  public void testJunit4() {
    assertSame(DependencyScope.TEST, getScope("junit4"));
  }

  public void testOrdinaryLibrary() {
    assertNull(getScope("ideaRt"));
  }

  public void testEmptyLibrary() {
    assertNull(getScope("empty"));
  }

  private DependencyScope getScope(String name) {
    LibraryOrderEntry entry = OrderEntryUtil.findLibraryOrderEntry(ModuleRootManager.getInstance(getModule()), name);
    assertNotNull(entry);
    Library library = entry.getLibrary();
    assertNotNull(library);
    return new JUnitDependencyScopeSuggester().getDefaultDependencyScope(library);
  }
}