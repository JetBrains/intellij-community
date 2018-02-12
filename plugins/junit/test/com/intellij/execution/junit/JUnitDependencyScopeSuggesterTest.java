/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.junit;

import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;

/**
 * @author nik
 */
public class JUnitDependencyScopeSuggesterTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("junit3", JavaSdkUtil.getJunit3JarPath());
    moduleBuilder.addLibrary("junit4", ArrayUtil.toStringArray(JavaSdkUtil.getJUnit4JarPaths()));
    moduleBuilder.addLibrary("ideaRt", JavaSdkUtil.getIdeaRtJarPath());
    moduleBuilder.addLibrary("empty");
  }

  public void testJunit3() {
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
    LibraryOrderEntry entry = OrderEntryUtil.findLibraryOrderEntry(ModuleRootManager.getInstance(myModule), name);
    assertNotNull(entry);
    Library library = entry.getLibrary();
    assertNotNull(library);
    return new JUnitDependencyScopeSuggester().getDefaultDependencyScope(library);
  }
}