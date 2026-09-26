// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GlibcVersionTest {
  /// Exercises the real `confstr` downcall. This is the only test that proves the binding.
  @Test void readsTheVersionOnLinux() {
    assumeTrue(OS.CURRENT == OS.Linux);

    assertThat(GlibcVersion.INSTANCE.getCurrent()).isNotBlank();
  }

  /// The probe must stay silent off Linux, because the callers treat `null` as "unknown".
  @Test void staysNullOnOtherSystems() {
    assumeTrue(OS.CURRENT != OS.Linux);

    assertThat(GlibcVersion.INSTANCE.getCurrent()).isNull();
  }
}
