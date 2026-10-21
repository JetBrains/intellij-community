// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.mock.MockProject
import com.intellij.openapi.util.Disposer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class FrameInitializedEarlyTest {
  private val disposable = Disposer.newDisposable()

  @AfterEach
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun `an existing project initializes its frame early`() {
    assertThat(isFrameInitializedEarly(OpenProjectTask {})).isTrue()
  }

  @Test
  fun `a new project initializes its frame early only when its opener asks`() {
    assertThat(isFrameInitializedEarly(OpenProjectTask { isNewProject = true })).isFalse()
    assertThat(isFrameInitializedEarly(OpenProjectTask {
      isNewProject = true
      initFrameEarlyForNewProject = true
    })).isTrue()
  }

  @Test
  fun `a before-open task or a given project keeps the frame initialization late`() {
    assertThat(isFrameInitializedEarly(OpenProjectTask {
      isNewProject = true
      initFrameEarlyForNewProject = true
      beforeOpen = { true }
    })).isFalse()
    assertThat(isFrameInitializedEarly(OpenProjectTask {
      initFrameEarlyForNewProject = true
      project = MockProject(null, disposable)
    })).isFalse()
  }
}
