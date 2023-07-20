// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PluginPlatformInfoTest extends LightJavaCodeInsightFixtureTestCase {

  public void testGradle2020_1() {
    doTestResolved("something:2020.1", "201");
  }

  public void testGradle2016_2() {
    doTestResolved("something:2016.2", "162");
  }

  public void testGradle193_6911_18() {
    doTestResolved("something:193.6911.18", "193.6911.18");
  }

  public void testGradle202_6397_EAP_CANDIDATE_SNAPSHOT() {
    doTestResolved("something:202.6397-EAP-CANDIDATE-SNAPSHOT", "202.6397");
  }

  public void testGradle201_EAP_SNAPSHOT() {
    doTestResolved("something:201-EAP-SNAPSHOT", "201");
  }

  public void testGradleLocalPath_IU_Snapshot() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:IU-222.SNAPSHOT", "222.SNAPSHOT");
  }

  public void testGradleLocalPath_IU_2020_1() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:IU-2020.1", "201.0");
  }

  public void testGradleLocalPath_IC_2021_3_3() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:IC-213.7172.25", "213.7172.25");
  }

  public void testGradleLocalPath_AC_2021_3_3() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:OC-213.7172.21", "213.7172.21");
  }

  private void doTestResolved(String libraryName, String expectedBuildNumber) {
    setupPlatformLibrary(libraryName);

    try {
      final PluginPlatformInfo info = PluginPlatformInfo.forModule(getModule());
      assertEquals(PluginPlatformInfo.PlatformResolveStatus.GRADLE, info.getResolveStatus());
      assertEquals(BuildNumber.fromString(expectedBuildNumber), info.getSinceBuildNumber());
      assertNull(info.getMainIdeaPlugin());
    }
    finally {
      removePlatformLibrary(libraryName);
    }
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

  public void testGradleUnresolvedLibraryMissingBranchVersion() {
    doTestUnresolved("something:2012345");
  }

  private void doTestUnresolved(@Nullable String libraryName) {
    if (libraryName != null) {
      setupPlatformLibrary(libraryName);
    }

    try {
      final PluginPlatformInfo info = PluginPlatformInfo.forModule(getModule());
      assertEquals(PluginPlatformInfo.PlatformResolveStatus.UNRESOLVED, info.getResolveStatus());
      assertNull(info.getSinceBuildNumber());
    }
    finally {
      if (libraryName != null) {
        removePlatformLibrary(libraryName);
      }
    }
  }

  private void setupPlatformLibrary(String libraryName) {
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    File file = new File(platformApiJar);
    PsiTestUtil.addLibrary(getModule(), libraryName, file.getParent(), file.getName());
  }

  private void removePlatformLibrary(String libraryName) {
    Library library = LibraryUtil.findLibrary(getModule(), libraryName);
    PsiTestUtil.removeLibrary(getModule(), library);
  }
}
