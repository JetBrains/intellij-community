// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PluginPlatformInfoTest extends JavaCodeInsightFixtureTestCase {

  public void testGradle2020_1() {
    doTestResolved("something:2020.1", "201");
  }

  public void testGradle2016_2() {
    doTestResolved("something:2016.2", "162");
  }

  private void doTestResolved(String libraryName, String expectedBuildNumber) {
    setupPlatformLibrary(libraryName);

    final PluginPlatformInfo info = PluginPlatformInfo.forModule(getModule());
    assertEquals(PluginPlatformInfo.PlatformResolveStatus.GRADLE, info.getResolveStatus());
    assertEquals(BuildNumber.fromString(expectedBuildNumber), info.getSinceBuildNumber());
    assertNull(info.getMainIdeaPlugin());
  }

  public void testGradleUnresolvedNoPlatformClass() {
    doTestUnresolved(null);
  }

  public void testGradleUnresolvedPlatformClassNotInLibrary() {
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
    doTestUnresolved(null);
  }

  public void testGradleUnresolvedLibraryNameNoVersion() {
    doTestUnresolved("something");
  }

  public void testGradleUnresolvedLibraryBadVersion() {
    doTestUnresolved("something:20.");
  }

  public void testGradleUnresolvedLibraryBadVersionPrefix() {
    doTestUnresolved("something:123.45");
  }

  public void testGradleUnresolvedLibraryMissingBranchVersion() {
    doTestUnresolved("something:2012345");
  }

  private void doTestUnresolved(@Nullable String libraryName) {
    if (libraryName != null) {
      setupPlatformLibrary(libraryName);
    }

    final PluginPlatformInfo info = PluginPlatformInfo.forModule(getModule());
    assertEquals(PluginPlatformInfo.PlatformResolveStatus.UNRESOLVED, info.getResolveStatus());
    assertNull(info.getSinceBuildNumber());
  }

  private void setupPlatformLibrary(String libraryName) {
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    File file = new File(platformApiJar);
    PsiTestUtil.addLibrary(getModule(), libraryName, file.getParent(), file.getName());
  }
}