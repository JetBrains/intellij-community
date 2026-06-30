// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AgentSessionSurfacesTest {
  @Test
  fun fromNormalizesSurfaceIds() {
    val surfaceId = AgentSessionSurfaceId.from("  Future.Surface-1  ")

    assertThat(surfaceId.value).isEqualTo("future.surface-1")
    assertThat(surfaceId.toString()).isEqualTo("future.surface-1")
  }

  @Test
  fun fromRejectsInvalidSurfaceIds() {
    assertThatThrownBy { AgentSessionSurfaceId.from("") }
      .isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { AgentSessionSurfaceId.from("1future") }
      .isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { AgentSessionSurfaceId.from("future surface") }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun fromOrNullNormalizesOrIgnoresInvalidSurfaceIds() {
    assertThat(AgentSessionSurfaceId.fromOrNull("  TUI.v2  ")?.value).isEqualTo("tui.v2")
    assertThat(AgentSessionSurfaceId.fromOrNull(null)).isNull()
    assertThat(AgentSessionSurfaceId.fromOrNull("   ")).isNull()
    assertThat(AgentSessionSurfaceId.fromOrNull("future surface")).isNull()
  }
}
