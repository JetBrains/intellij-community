// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.nio.file.Path

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchPathUtilTest {
  @Test
  fun parsesAndNormalizesValidPaths() {
    val nativePath = Path.of("workspace", "child").toString()

    assertThat(parseAgentWorkbenchPathOrNull(nativePath)).isEqualTo(Path.of("workspace", "child"))
    assertThat(normalizeAgentWorkbenchPath(nativePath)).isEqualTo("workspace/child")
    assertThat(normalizeAgentWorkbenchPathOrNull(nativePath)).isEqualTo("workspace/child")
  }

  @Test
  fun preservesInvalidPathsWhenNormalizationFails() {
    val invalidPath = "bad" + '\u0000' + "path"

    assertThat(parseAgentWorkbenchPathOrNull(invalidPath)).isNull()
    assertThat(normalizeAgentWorkbenchPath(invalidPath)).isEqualTo(invalidPath)
    assertThat(normalizeAgentWorkbenchPathOrNull(invalidPath)).isNull()
  }
}
