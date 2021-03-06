// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.system;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SystemInfoTest {
  @Test
  public void jvmCpuArchitectureDetection() {
    CpuArch arch = CpuArch.CURRENT;
    assertThat(arch).isNotIn(CpuArch.OTHER, CpuArch.UNKNOWN);
    assertThat(arch.width).isGreaterThan(0);
  }
}
