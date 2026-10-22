// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.productMode.ProductMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CurrentProductModeTest {
  @Test
  fun `the transition table has no cycle`() {
    for (start in ProductMode.entries) {
      val seen = mutableSetOf(start)
      var frontier = CurrentProductMode.allowedTargets(start)
      while (frontier.isNotEmpty()) {
        val revisited = frontier.intersect(seen)
        assertThat(revisited)
          .describedAs("a transition from '${start.id}' returns to a mode it already left")
          .isEmpty()
        seen.addAll(frontier)
        frontier = frontier.flatMapTo(mutableSetOf()) { CurrentProductMode.allowedTargets(it) }
      }
    }
  }

  @Test
  fun `a light process reaches the frontend mode in two steps`() {
    assertThat(CurrentProductMode.allowedTargets(ProductMode.LIGHT_REMOTE))
      .containsExactly(ProductMode.LIGHT_WITH_RD_CONNECTION)
    assertThat(CurrentProductMode.allowedTargets(ProductMode.LIGHT_WITH_RD_CONNECTION))
      .containsExactly(ProductMode.FRONTEND)
  }

  @Test
  fun `a standalone light process reaches the monolith mode in one step`() {
    assertThat(CurrentProductMode.allowedTargets(ProductMode.LIGHT_MONOLITH))
      .containsExactly(ProductMode.MONOLITH)
  }

  @Test
  fun `a process that started light returns from the monolith mode`() {
    assertThat(CurrentProductMode.allowedTargets(ProductMode.MONOLITH, initial = ProductMode.LIGHT_MONOLITH))
      .containsExactly(ProductMode.LIGHT_MONOLITH)
    for (initial in ProductMode.entries - ProductMode.LIGHT_MONOLITH) {
      assertThat(CurrentProductMode.allowedTargets(ProductMode.MONOLITH, initial))
        .describedAs("targets of 'monolith' in a process that started in '${initial.id}'")
        .isEmpty()
    }
  }

  @Test
  fun `a mode with no declared target cannot move`() {
    for (mode in listOf(ProductMode.MONOLITH, ProductMode.FRONTEND, ProductMode.BACKEND, ProductMode.LANGUAGE_SERVER)) {
      assertThat(CurrentProductMode.allowedTargets(mode))
        .describedAs("targets of '${mode.id}'")
        .isEmpty()
    }
  }

  @Test
  fun `a transition succeeds one time only`() {
    CurrentProductMode.withProductMode(ProductMode.LIGHT_REMOTE) {
      assertThat(CurrentProductMode.transitionTo(ProductMode.LIGHT_WITH_RD_CONNECTION)).isTrue()
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.LIGHT_WITH_RD_CONNECTION)
      // the mode already moved, so the caller must not load the modules a second time
      assertThat(CurrentProductMode.transitionTo(ProductMode.LIGHT_WITH_RD_CONNECTION)).isFalse()
    }
    CurrentProductMode.withProductMode(ProductMode.LIGHT_MONOLITH) {
      assertThat(CurrentProductMode.transitionTo(ProductMode.MONOLITH)).isTrue()
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.MONOLITH)
      assertThat(CurrentProductMode.transitionTo(ProductMode.MONOLITH)).isFalse()
    }
  }

  @Test
  fun `the derived properties of each mode`() {
    // isLight, isFrontendProcess, isMonolithProcess, isLightWithoutRemoteApi
    val expected = mapOf(
      ProductMode.MONOLITH to listOf(false, false, true, false),
      ProductMode.FRONTEND to listOf(false, true, false, false),
      ProductMode.BACKEND to listOf(false, false, false, false),
      ProductMode.LIGHT_REMOTE to listOf(true, true, false, true),
      ProductMode.LIGHT_WITH_RD_CONNECTION to listOf(true, true, false, false),
      ProductMode.LIGHT_MONOLITH to listOf(true, false, true, true),
      ProductMode.LANGUAGE_SERVER to listOf(false, false, false, false),
    )
    assertThat(expected.keys).containsExactlyInAnyOrderElementsOf(ProductMode.entries)
    for ((mode, flags) in expected) {
      assertThat(listOf(mode.isLight, mode.isFrontendProcess, mode.isMonolithProcess, mode.isLightWithoutRemoteApi))
        .describedAs("isLight, isFrontendProcess, isMonolithProcess, isLightWithoutRemoteApi of '${mode.id}'")
        .isEqualTo(flags)
    }
  }

  @Test
  fun `a transition to an unreachable mode fails and keeps the mode`() {
    CurrentProductMode.withProductMode(ProductMode.LIGHT_REMOTE) {
      assertThat(CurrentProductMode.transitionTo(ProductMode.FRONTEND)).isFalse()
      assertThat(CurrentProductMode.transitionTo(ProductMode.MONOLITH)).isFalse()
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.LIGHT_REMOTE)
    }
    CurrentProductMode.withProductMode(ProductMode.LIGHT_MONOLITH) {
      assertThat(CurrentProductMode.transitionTo(ProductMode.LIGHT_WITH_RD_CONNECTION)).isFalse()
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.LIGHT_MONOLITH)
    }
  }

  @Test
  fun `withProductMode restores the previous mode`() {
    val before = CurrentProductMode.value
    CurrentProductMode.withProductMode(ProductMode.LIGHT_REMOTE) {
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.LIGHT_REMOTE)
    }
    assertThat(CurrentProductMode.value).isEqualTo(before)
  }
}
