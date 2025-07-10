// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SystemInfoTest {
  @Test void jvmCpuArchitectureDetection() {
    var arch = CpuArch.CURRENT;
    assertThat(arch).isNotIn(CpuArch.OTHER, CpuArch.UNKNOWN);
    assertThat(arch.width).isGreaterThan(0);
  }

  @Test void osDetection() {
    assertThat(OS.CURRENT).isNotEqualTo(OS.Other);
  }

  @Test void windowsBuildNumberReading() {
    assumeTrue(OS.CURRENT == OS.Windows);

    var osInfo = (OS.WindowsInfo)OS.CURRENT.getOsInfo();
    assertThat(osInfo.getBuildNumber()).isGreaterThan(10240);
  }

  @Test void unixReleaseDataReading() {
    assumeTrue(OS.isGenericUnix());
    assumeTrue(Files.exists(Path.of("/etc/os-release")));

    var osInfo = (OS.UnixInfo)OS.CURRENT.getOsInfo();
    assertThat(osInfo.getDistro()).isNotBlank();
    assertThat(osInfo.getRelease()).isNotBlank();
  }

  @Test void linuxFeatures() {
    assumeTrue(OS.CURRENT == OS.Linux);

    var osInfo = (OS.LinuxInfo)OS.CURRENT.getOsInfo();
    assertThat(osInfo.getGlibcVersion()).isNotBlank();
  }
}
