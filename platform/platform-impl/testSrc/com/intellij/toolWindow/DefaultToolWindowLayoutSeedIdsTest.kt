// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultToolWindowLayoutSeedIdsTest {
  @Test
  fun holdsOnlyTheWindowThatOptsIn() {
    val builder = DefaultToolWindowLayoutBuilderImpl()
    builder.left.addOrUpdate("Project")
    builder.left.addOrUpdate("Agent") { seedIntoExistingLayout() }

    assertThat(builder.seededIntoExistingLayoutIds()).containsExactly("Agent")
  }

  @Test
  fun aLaterUpdateKeepsTheOptIn() {
    val builder = DefaultToolWindowLayoutBuilderImpl()
    builder.left.addOrUpdate("Agent") { seedIntoExistingLayout() }

    builder.right.addOrUpdate("Agent") { weight = 0.4f }

    assertThat(builder.seededIntoExistingLayoutIds()).containsExactly("Agent")
  }

  @Test
  fun aRemovedWindowIsNotSeeded() {
    val builder = DefaultToolWindowLayoutBuilderImpl()
    builder.left.addOrUpdate("Agent") { seedIntoExistingLayout() }

    builder.removeAll { it.id == "Agent" }

    assertThat(builder.seededIntoExistingLayoutIds()).isEmpty()
  }

  @Test
  fun noWindowOptsInByDefault() {
    val builder = DefaultToolWindowLayoutBuilderImpl()
    builder.left.addPlatformDefaultsV2()
    builder.right.addPlatformDefaultsV2()
    builder.bottom.addPlatformDefaultsV2()

    assertThat(builder.seededIntoExistingLayoutIds()).isEmpty()
  }
}
