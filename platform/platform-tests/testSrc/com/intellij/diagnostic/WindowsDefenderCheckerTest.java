// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.mock.MockProject;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.SystemIndependent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestApplication
public class WindowsDefenderCheckerTest {
  @BeforeAll static void beforeAll() {
    assumeWindows();
  }

  @Test void defenderStatusDetection() {
    assertNotNull(WindowsDefenderChecker.getInstance().isRealTimeProtectionEnabled());
  }

  @Test void downloadDirDetection() {
    var downloadDir = Path.of(System.getProperty("user.home"), "Downloads");
    assumeTrue(Files.isDirectory(downloadDir));
    assertTrue(WindowsDefenderChecker.getInstance().isUntrustworthyLocation(downloadDir.resolve("project")));
  }

  @Test
  void excludeSavedPathsForSameProject() {
    excludeSavedPathForProject("C:\\Projects\\project1", "C:\\Projects\\project1", true);
  }

  @Test
  void excludeSavedPathsForOtherProject() {
    excludeSavedPathForProject("C:\\Projects\\project1", "C:\\Projects\\project2", false);
  }

  @Test
  void excludeSavedPathsForRootProject() {
    excludeSavedPathForProject("C:\\Projects", "C:\\Projects\\project1", true);
  }

  void excludeSavedPathForProject(String pathToExclude, String projectPath, boolean expectedExcluding) {
    Disposable disposable = Disposer.newDisposable();
    try {
      MockProject project = new MockProjectEx(disposable) {
        @Override
        public @SystemIndependent String getBasePath() {
          return projectPath;
        }
      };
      WindowsDefenderChecker defenderChecker = WindowsDefenderChecker.getInstance();
      defenderChecker.setPathsToExclude(List.of(Path.of(pathToExclude)));
      boolean isExcluded = defenderChecker.hasPathsToExclude(project);
      assertEquals(isExcluded, expectedExcluding);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }
}
