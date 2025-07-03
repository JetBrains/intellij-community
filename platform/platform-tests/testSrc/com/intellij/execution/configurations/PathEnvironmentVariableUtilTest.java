// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.util.system.OS;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PathEnvironmentVariableUtilTest {
  @Test void isOnPathContract() {
    assertThatCode(() -> PathEnvironmentVariableUtil.isOnPath("bad\\path")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void isOnPathPositive() {
    var shell = Path.of(OS.CURRENT == OS.Windows ? "C:\\Windows\\System32\\cmd.exe" : "/bin/sh");
    assumeTrue(Files.isExecutable(shell));
    assertThat(PathEnvironmentVariableUtil.isOnPath(shell.getFileName().toString())).isTrue();
  }

  @Test void isOnPathNegative() {
    assertThat(PathEnvironmentVariableUtil.isOnPath("no-one-in-his-right-mind-names-an-exec-like-this")).isFalse();
  }
}
