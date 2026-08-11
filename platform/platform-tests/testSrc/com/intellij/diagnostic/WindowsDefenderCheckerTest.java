// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.testFramework.junit5.TestApplication;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestApplication
public class WindowsDefenderCheckerTest {
  private static final Condition<Path> UNTRUSTWORTHY = new Condition<>(
    path -> WindowsDefenderChecker.getInstance().isUntrustworthyLocation(path),
    "untrustworthy"
  );

  @BeforeAll static void beforeAll() {
    assumeWindows();
  }

  @Test void defenderStatusDetection() {
    assertThat(WindowsDefenderChecker.getInstance().isRealTimeProtectionEnabled()).isNotNull();
  }

  @Test void downloadDirDetection() {
    var downloadDir = Path.of(System.getProperty("user.home"), "Downloads");
    assumeTrue(Files.isDirectory(downloadDir));
    assertThat(downloadDir).is(UNTRUSTWORTHY);
    assertThat(downloadDir.resolve("project")).is(UNTRUSTWORTHY);
  }

  @Test void otherUntrustworthyDirs() {
    assertThat(Path.of("M:\\")).is(UNTRUSTWORTHY);
    assertThat(Path.of("M:\\dir")).isNot(UNTRUSTWORTHY);

    var tempVar = System.getenv("TEMP");
    if (tempVar != null) {
      var tempDir = Path.of(tempVar);
      assertThat(tempDir).is(UNTRUSTWORTHY);
      assertThat(tempDir.resolve("project")).is(UNTRUSTWORTHY);
      assertThat(tempDir.getParent()).is(UNTRUSTWORTHY);
    }

    var userDir = Path.of(System.getProperty("user.home"));
    assertThat(userDir).is(UNTRUSTWORTHY);
    assertThat(userDir.getParent()).is(UNTRUSTWORTHY);
    assertThat(userDir.resolve("project")).isNot(UNTRUSTWORTHY);
  }
}
