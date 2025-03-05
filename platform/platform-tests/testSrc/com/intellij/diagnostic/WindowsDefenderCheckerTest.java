// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
