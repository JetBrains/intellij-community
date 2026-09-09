// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UnifiedPluginsPageApplyStateTest {
  @Test
  fun `disposal without pending apply requests one session close`() {
    val state = UnifiedPluginsPageApplyState()

    assertThat(state.dispose()).isTrue()
    assertThat(state.dispose()).isFalse()
  }

  @Test
  fun `disposal waits until every apply operation settles`() {
    val state = UnifiedPluginsPageApplyState()
    state.operationStarted()
    state.operationStarted()

    assertThat(state.dispose()).isFalse()
    assertThat(state.operationFinished(restartRequired = false)).isFalse()
    assertThat(state.operationFinished(restartRequired = false)).isTrue()
  }

  @Test
  fun `settled apply closes only after later disposal`() {
    val state = UnifiedPluginsPageApplyState()
    state.operationStarted()

    assertThat(state.operationFinished(restartRequired = false)).isFalse()
    assertThat(state.dispose()).isTrue()
  }

  @Test
  fun `restart flow retains the session after disposal`() {
    val state = UnifiedPluginsPageApplyState()
    state.operationStarted()

    assertThat(state.dispose()).isFalse()
    assertThat(state.operationFinished(restartRequired = true)).isFalse()
    assertThat(state.dispose()).isFalse()
  }

  @Test
  fun `restart flow retains the session when apply settles before disposal`() {
    val state = UnifiedPluginsPageApplyState()
    state.operationStarted()

    assertThat(state.operationFinished(restartRequired = true)).isFalse()
    assertThat(state.dispose()).isFalse()
  }

  @Test
  fun `cancel reset owns session closure after disposal`() {
    val state = UnifiedPluginsPageApplyState()
    state.operationStarted()
    assertThat(state.resetWithSessionRemovalStarted()).isTrue()

    assertThat(state.dispose()).isFalse()
    assertThat(state.operationFinished(restartRequired = false)).isFalse()
  }

  @Test
  fun `duplicate cancel reset does not submit again`() {
    val state = UnifiedPluginsPageApplyState()

    assertThat(state.resetWithSessionRemovalStarted()).isTrue()
    assertThat(state.resetWithSessionRemovalStarted()).isFalse()
  }

  @Test
  fun `failed cancel reset releases ownership before disposal`() {
    val state = UnifiedPluginsPageApplyState()
    assertThat(state.resetWithSessionRemovalStarted()).isTrue()

    assertThat(state.resetWithSessionRemovalFailed()).isFalse()
    assertThat(state.dispose()).isTrue()
  }

  @Test
  fun `failed cancel reset requests closure after disposal`() {
    val state = UnifiedPluginsPageApplyState()
    assertThat(state.resetWithSessionRemovalStarted()).isTrue()
    assertThat(state.dispose()).isFalse()

    assertThat(state.resetWithSessionRemovalFailed()).isTrue()
    assertThat(state.resetWithSessionRemovalFailed()).isFalse()
  }

  @Test
  fun `failed cancel reset waits for a pending apply operation`() {
    val state = UnifiedPluginsPageApplyState()
    state.operationStarted()
    assertThat(state.resetWithSessionRemovalStarted()).isTrue()
    assertThat(state.dispose()).isFalse()

    assertThat(state.resetWithSessionRemovalFailed()).isFalse()
    assertThat(state.operationFinished(restartRequired = false)).isTrue()
  }
}
