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
    assertThat(CurrentProductMode.allowedTargets(ProductMode.LIGHT))
      .containsExactly(ProductMode.LIGHT_WITH_RD_CONNECTION)
    assertThat(CurrentProductMode.allowedTargets(ProductMode.LIGHT_WITH_RD_CONNECTION))
      .containsExactly(ProductMode.FRONTEND)
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
    CurrentProductMode.withProductMode(ProductMode.LIGHT) {
      assertThat(CurrentProductMode.transitionTo(ProductMode.LIGHT_WITH_RD_CONNECTION)).isTrue()
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.LIGHT_WITH_RD_CONNECTION)
      // the mode already moved, so the caller must not load the modules a second time
      assertThat(CurrentProductMode.transitionTo(ProductMode.LIGHT_WITH_RD_CONNECTION)).isFalse()
    }
  }

  @Test
  fun `a transition to an unreachable mode fails and keeps the mode`() {
    CurrentProductMode.withProductMode(ProductMode.LIGHT) {
      assertThat(CurrentProductMode.transitionTo(ProductMode.FRONTEND)).isFalse()
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.LIGHT)
    }
  }

  @Test
  fun `withProductMode restores the previous mode`() {
    val before = CurrentProductMode.value
    CurrentProductMode.withProductMode(ProductMode.LIGHT) {
      assertThat(CurrentProductMode.value).isEqualTo(ProductMode.LIGHT)
    }
    assertThat(CurrentProductMode.value).isEqualTo(before)
  }
}
