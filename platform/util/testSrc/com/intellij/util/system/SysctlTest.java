// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SysctlTest {
  /// Exercises the real `sysctlbyname` downcall with the name that `MacHardwareInfo` reads.
  @Test void readsTheModelOnMacOS() {
    assumeTrue(OS.CURRENT == OS.macOS);

    assertThat(Sysctl.stringByName("hw.model")).isNotBlank().doesNotContain("\0");
    assertThat(MacHardwareInfo.INSTANCE.isMacbookNeo()).isNotNull();
  }

  /// An unknown name is `null`, not an error.
  @Test void unknownNameIsNull() {
    assumeTrue(OS.CURRENT == OS.macOS);

    assertThat(Sysctl.stringByName("hw.no_such_value")).isNull();
  }
}
