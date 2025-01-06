// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlatformMemoryUtilTest {
  @Test
  fun `test memory stats are available`() {
    val isSupportedPlatform = SystemInfo.isLinux
                              || SystemInfo.isWin10OrNewer && CpuArch.isIntel64()
                              || SystemInfo.isMac && CpuArch.isArm64()
    if (!isSupportedPlatform) return

    assertNotNull("Platform memory stats are unavailable",
                  PlatformMemoryUtil.getInstance().getCurrentProcessMemoryStats())
  }
}