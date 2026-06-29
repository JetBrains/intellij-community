// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions.backend.appserver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SharedCodexAppServerServiceTest {
  @Test
  fun yoloPrestartThreadOptionsUseWorkspaceWriteOnRequest() {
    val options = resolveCodexPrestartThreadOptions(
      cwd = "/work/project",
      yolo = true,
      model = "gpt-5.1-codex",
      reasoningEffort = "high",
    )

    assertThat(options.cwd).isEqualTo("/work/project")
    assertThat(options.model).isEqualTo("gpt-5.1-codex")
    assertThat(options.reasoningEffort).isEqualTo("high")
    assertThat(options.approvalPolicy).isEqualTo("on-request")
    assertThat(options.sandbox).isEqualTo("workspace-write")
  }

  @Test
  fun standardPrestartThreadOptionsDoNotSetYoloParams() {
    val options = resolveCodexPrestartThreadOptions(
      cwd = "/work/project",
      yolo = false,
      model = "gpt-5.1-codex",
      reasoningEffort = "medium",
    )

    assertThat(options.cwd).isEqualTo("/work/project")
    assertThat(options.model).isEqualTo("gpt-5.1-codex")
    assertThat(options.reasoningEffort).isEqualTo("medium")
    assertThat(options.approvalPolicy).isNull()
    assertThat(options.sandbox).isNull()
  }
}
