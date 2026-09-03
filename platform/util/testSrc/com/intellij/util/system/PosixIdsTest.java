// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PosixIdsTest {
  /// Exercises the real `getuid` and `geteuid` downcalls. A test process runs without a setuid bit, so the two ids match.
  @Test void readsTheUserIds() {
    assumeTrue(OS.CURRENT != OS.Windows);

    int uid = PosixIds.getuid();
    assertThat(uid).isNotNegative();
    assertThat(PosixIds.geteuid()).isEqualTo(uid);
  }
}
