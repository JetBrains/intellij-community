// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.util.system.OS;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemoryLayout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MemoryUtilTest {
  /// `struct vm_statistics64` from `<mach/vm_statistics.h>`, whose two read fields sit in the `natural_t` head.
  @Test void vmStatistics64Layout() {
    assertThat(MemoryUtil.VM_STATISTICS64.byteSize()).isEqualTo(152);
    assertThat(MemoryUtil.VM_STATISTICS64.byteOffset(MemoryLayout.PathElement.groupElement("free_count"))).isEqualTo(0);
    assertThat(MemoryUtil.VM_STATISTICS64.byteOffset(MemoryLayout.PathElement.groupElement("inactive_count"))).isEqualTo(8);
    assertThat(MemoryUtil.VM_STATISTICS64.byteOffset(MemoryLayout.PathElement.groupElement("speculative_count"))).isEqualTo(92);
  }

  /// Exercises the real Mach downcalls. This is the only test that proves the binding.
  @Test void readsUnusedMemoryOnMacOS() {
    assumeTrue(OS.CURRENT == OS.macOS);

    assertThat(MemoryUtil.getUnusedMemory()).isNotNull().isPositive();
  }

  /// The probe must stay silent off macOS, because the caller falls back to the MX bean on `null`.
  @Test void staysNullOnOtherSystems() {
    assumeTrue(OS.CURRENT != OS.macOS);

    assertThat(MemoryUtil.getUnusedMemory()).isNull();
  }
}
