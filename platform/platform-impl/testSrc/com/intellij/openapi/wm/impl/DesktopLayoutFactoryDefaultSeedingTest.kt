// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindowAnchor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DesktopLayoutFactoryDefaultSeedingTest {
  @Test
  fun seedsAMissingEntryAfterItsPresentDefaultPredecessor() {
    val layout = layoutOf(info("Project", 0), info("Commit", 1), info("Structure", 2, split = true))
    val factoryDefault = layoutOf(
      info("Project", 0),
      info("Agent", 1) {
        weight = 0.25f
        isVisible = true
      },
      info("Commit", 2),
      info("Structure", 3, split = true),
    )

    val affected = seedFromFactoryDefault(layout, listOf("Project", "Agent", "Commit", "Structure"), factoryDefault)

    val agent = checkNotNull(layout.getInfo("Agent"))
    assertThat(agent.anchor).isEqualTo(ToolWindowAnchor.LEFT)
    assertThat(agent.isSplit).isFalse()
    assertThat(agent.order).isEqualTo(1)
    assertThat(agent.weight).isEqualTo(0.25f)
    assertThat(agent.isShowStripeButton).isTrue()
    assertThat(agent.isVisible).isFalse()
    assertThat(layout.getInfo("Commit")!!.order).isEqualTo(2)
    assertThat(layout.getInfo("Structure")!!.order).isEqualTo(3)
    assertThat(affected.map { it.id }).containsExactlyInAnyOrder("Agent", "Commit", "Structure")
    assertThat(primaryLeftIds(layout)).containsExactly("Project", "Agent", "Commit")
  }

  @Test
  fun keepsAPresentEntryWithTheButtonRemoved() {
    val layout = layoutOf(info("Project", 0), info("Agent", 5) { isShowStripeButton = false }, info("Commit", 1))
    val factoryDefault = layoutOf(info("Project", 0), info("Agent", 1), info("Commit", 2))

    val affected = seedFromFactoryDefault(layout, listOf("Project", "Agent", "Commit"), factoryDefault)

    assertThat(affected).isEmpty()
    val agent = checkNotNull(layout.getInfo("Agent"))
    assertThat(agent.isShowStripeButton).isFalse()
    assertThat(agent.order).isEqualTo(5)
    assertThat(layout.getInfo("Commit")!!.order).isEqualTo(1)
  }

  @Test
  fun ignoresAnIdTheFactoryDefaultLacks() {
    val layout = layoutOf(info("Project", 0))
    val factoryDefault = layoutOf(info("Project", 0))

    val affected = seedFromFactoryDefault(layout, listOf("Project", "Other"), factoryDefault)

    assertThat(affected).isEmpty()
    assertThat(layout.getInfo("Other")).isNull()
  }

  @Test
  fun appendsWhenNoDefaultPredecessorIsPresent() {
    val layout = layoutOf(info("Commit", 0))
    val factoryDefault = layoutOf(info("Project", 0), info("Agent", 1), info("Commit", 2))

    seedFromFactoryDefault(layout, listOf("Agent"), factoryDefault)

    assertThat(layout.getInfo("Commit")!!.order).isEqualTo(0)
    assertThat(layout.getInfo("Agent")!!.order).isEqualTo(1)
  }

  @Test
  fun aPredecessorMovedToAnotherGroupDoesNotPlaceTheEntry() {
    val layout = layoutOf(info("Project", 0, split = true), info("Commit", 1), info("Database", 0, anchor = ToolWindowAnchor.RIGHT))
    val factoryDefault = layoutOf(info("Project", 0), info("Agent", 1), info("Commit", 2))

    seedFromFactoryDefault(layout, listOf("Agent"), factoryDefault)

    assertThat(layout.getInfo("Agent")!!.order).isEqualTo(2)
    assertThat(layout.getInfo("Project")!!.order).isEqualTo(0)
    assertThat(layout.getInfo("Commit")!!.order).isEqualTo(1)
    assertThat(layout.getInfo("Database")!!.order).isEqualTo(0)
  }

  @Test
  fun aPredecessorMovedToAnotherStripeDoesNotPlaceTheEntry() {
    val layout = layoutOf(info("Project", 0, anchor = ToolWindowAnchor.RIGHT), info("Commit", 0))
    val factoryDefault = layoutOf(info("Project", 0), info("Agent", 1), info("Commit", 2))

    seedFromFactoryDefault(layout, listOf("Agent"), factoryDefault)

    val agent = checkNotNull(layout.getInfo("Agent"))
    assertThat(agent.anchor).isEqualTo(ToolWindowAnchor.LEFT)
    assertThat(agent.order).isEqualTo(1)
    assertThat(layout.getInfo("Project")!!.order).isEqualTo(0)
  }

  @Test
  fun seedsSeveralMissingEntriesInDefaultOrder() {
    val layout = layoutOf(info("Project", 0), info("Commit", 1))
    val factoryDefault = layoutOf(info("Project", 0), info("First", 1), info("Second", 2), info("Commit", 3))

    seedFromFactoryDefault(layout, listOf("Second", "First"), factoryDefault)

    assertThat(primaryLeftIds(layout)).containsExactly("Project", "First", "Second", "Commit")
  }

  @Test
  fun keepsAHiddenStripeButtonOfTheFactoryDefault() {
    val layout = layoutOf(info("Project", 0))
    val factoryDefault = layoutOf(info("Project", 0), info("Quiet", 1) { isShowStripeButton = false })

    seedFromFactoryDefault(layout, listOf("Quiet"), factoryDefault)

    assertThat(layout.getInfo("Quiet")!!.isShowStripeButton).isFalse()
  }
}

private fun info(
  id: String,
  order: Int,
  anchor: ToolWindowAnchor = ToolWindowAnchor.LEFT,
  split: Boolean = false,
  configure: WindowInfoImpl.() -> Unit = {},
): WindowInfoImpl {
  return WindowInfoImpl().apply {
    this.id = id
    this.order = order
    this.anchor = anchor
    isSplit = split
    configure()
  }
}

private fun layoutOf(vararg infos: WindowInfoImpl): DesktopLayout {
  return DesktopLayout(infos.associateByTo(HashMap()) { checkNotNull(it.id) })
}

private fun primaryLeftIds(layout: DesktopLayout): List<String> {
  return layout.getInfos().values
    .filter { it.anchor == ToolWindowAnchor.LEFT && !it.isSplit }
    .sortedBy { it.order }
    .map { checkNotNull(it.id) }
}
