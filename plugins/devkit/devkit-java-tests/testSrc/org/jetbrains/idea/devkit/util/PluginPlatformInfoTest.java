// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class PluginPlatformInfoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testGradle2020_1() {
    doTestResolved("something:2020.1", "201");
  }

  @Test
  public void testGradle2016_2() {
    doTestResolved("something:2016.2", "162");
  }

  @Test
  public void testGradle193_6911_18() {
    doTestResolved("something:193.6911.18", "193.6911.18");
  }

  @Test
  public void testGradle202_6397_EAP_CANDIDATE_SNAPSHOT() {
    doTestResolved("something:202.6397-EAP-CANDIDATE-SNAPSHOT", "202.6397");
  }

  @Test
  public void testGradle201_EAP_SNAPSHOT() {
    doTestResolved("something:201-EAP-SNAPSHOT", "201");
  }

  @Test
  public void testGradleLocalPath_IU_Snapshot() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:IU-222.SNAPSHOT", "222.SNAPSHOT");
  }

  @Test
  public void testGradleLocalPath_IU_2020_1() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:IU-2020.1", "201.0");
  }

  @Test
  public void testGradleLocalPath_IC_2021_3_3() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:IC-213.7172.25", "213.7172.25");
  }

  @Test
  public void testGradleLocalPath_AC_2021_3_3() {
    doTestResolved("Gradle: com.jetbrains:ideaLocal:OC-213.7172.21", "213.7172.21");
  }

  private void doTestResolved(String libraryName, String expectedBuildNumber) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupPlatformLibrary(libraryName);

    try {
      final PluginPlatformInfo info = ReadAction.computeBlocking(() -> PluginPlatformInfo.forModule(myFixture.getModule()));
      assertEquals(PluginPlatformInfo.PlatformResolveStatus.GRADLE, info.getResolveStatus());
      assertEquals(BuildNumber.fromString(expectedBuildNumber), info.getSinceBuildNumber());
      assertNull(info.getMainIdeaPlugin());
    }
    finally {
      removePlatformLibrary(libraryName);
    }
  }

  @Test
  public void testGradleUnresolvedNoPlatformClass() {
    doTestUnresolved(null);
  }

  @Test
  public void testGradleUnresolvedPlatformClassNotInLibrary() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
    doTestUnresolved(null);
  }

  @Test
  public void testGradleUnresolvedLibraryNameNoVersion() {
    doTestUnresolved("something");
  }

  @Test
  public void testGradleUnresolvedLibraryBadVersion() {
    doTestUnresolved("something:20.");
  }

  @Test
  public void testGradleUnresolvedLibraryMissingBranchVersion() {
    doTestUnresolved("something:2012345");
  }

  private void doTestUnresolved(@Nullable String libraryName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (libraryName != null) {
      setupPlatformLibrary(libraryName);
    }

    try {
      final PluginPlatformInfo info = ReadAction.computeBlocking(() -> PluginPlatformInfo.forModule(myFixture.getModule()));
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    File file = new File(platformApiJar);
    PsiTestUtil.addLibrary(myFixture.getModule(), libraryName, file.getParent(), file.getName());
  }

  private void removePlatformLibrary(String libraryName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Library library = LibraryUtil.findLibrary(myFixture.getModule(), libraryName);
    PsiTestUtil.removeLibrary(myFixture.getModule(), library);
  }
}
