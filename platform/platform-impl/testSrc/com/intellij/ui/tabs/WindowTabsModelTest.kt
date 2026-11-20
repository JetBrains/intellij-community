// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WindowTabsModelTest {
  private fun model(vararg items: String, group: List<String> = items.toList()): WindowTabsModel<String> {
    val model = WindowTabsModel<String>()
    for (item in items) {
      model.add(item)
    }
    for (item in group) {
      model.join(item)
    }
    return model
  }

  @Test
  fun `a new item is outside the tab group`() {
    val model = model("a", "b", group = listOf("a"))

    assertThat(model.getItems()).containsExactly("a", "b")
    assertThat(model.getGroupItems()).containsExactly("a")
    assertThat(model.isInGroup("b")).isFalse()
  }

  @Test
  fun `an item joins the tab group only once`() {
    val model = model("a", group = emptyList())

    assertThat(model.join("a")).isTrue()
    assertThat(model.join("a")).isFalse()
    assertThat(model.join("unknown")).isFalse()
    assertThat(model.getGroupItems()).containsExactly("a")
  }

  @Test
  fun `an item is tabbed when the group has another item`() {
    val model = model("a", "b", "c", group = listOf("a"))

    assertThat(model.isTabbed("a")).isFalse()

    model.join("b")

    assertThat(model.isTabbed("a")).isTrue()
    assertThat(model.isTabbed("b")).isTrue()
    assertThat(model.isTabbed("c")).isFalse()
  }

  @Test
  fun `select changes the current item only for a known other item`() {
    val model = model("a", "b")

    assertThat(model.select("a")).isTrue()
    assertThat(model.select("a")).isFalse()
    assertThat(model.select("unknown")).isFalse()
    assertThat(model.current).isEqualTo("a")

    assertThat(model.select("b")).isTrue()
    assertThat(model.current).isEqualTo("b")
  }

  @Test
  fun `an item outside the group can be the current item`() {
    val model = model("a", "b", group = listOf("a"))
    model.select("a")

    assertThat(model.select("b")).isTrue()
    assertThat(model.current).isEqualTo("b")
  }

  @Test
  fun `the removal of the current item returns the item at its position`() {
    val model = model("a", "b", "c")
    model.select("b")

    assertThat(model.remove("b")).isEqualTo("c")
    assertThat(model.current).isNull()
    assertThat(model.getItems()).containsExactly("a", "c")
    assertThat(model.getGroupItems()).containsExactly("a", "c")
  }

  @Test
  fun `the removal of the last current item returns the item before it`() {
    val model = model("a", "b", "c")
    model.select("c")

    assertThat(model.remove("c")).isEqualTo("b")
  }

  @Test
  fun `the removal of the only current item returns null`() {
    val model = model("a")
    model.select("a")

    assertThat(model.remove("a")).isNull()
    assertThat(model.current).isNull()
    assertThat(model.getItems()).isEmpty()
  }

  @Test
  fun `the removal of an item that is not current keeps the current item`() {
    val model = model("a", "b", "c")
    model.select("b")

    assertThat(model.remove("a")).isNull()
    assertThat(model.remove("unknown")).isNull()
    assertThat(model.current).isEqualTo("b")
    assertThat(model.getItems()).containsExactly("b", "c")
  }

  @Test
  fun `the adjacent item goes in a circle and skips the items outside the group`() {
    val model = model("a", "x", "b", "c", group = listOf("a", "b", "c"))

    assertThat(model.getAdjacentGroupItem("a", next = true)).isEqualTo("b")
    assertThat(model.getAdjacentGroupItem("c", next = true)).isEqualTo("a")
    assertThat(model.getAdjacentGroupItem("a", next = false)).isEqualTo("c")
    assertThat(model.getAdjacentGroupItem("b", next = false)).isEqualTo("a")
  }

  @Test
  fun `the adjacent item is null without a group of two`() {
    val model = model("a", "x", group = listOf("a"))

    assertThat(model.getAdjacentGroupItem("a", next = true)).isNull()

    model.add("b")
    model.join("b")

    assertThat(model.getAdjacentGroupItem("x", next = true)).isNull()
    assertThat(model.getAdjacentGroupItem("a", next = true)).isEqualTo("b")
  }

  @Test
  fun `a move uses the index of the tab group`() {
    val model = model("a", "x", "b", "c", group = listOf("a", "b", "c"))

    model.moveToGroupIndex("c", 0)
    assertThat(model.getGroupItems()).containsExactly("c", "a", "b")

    model.moveToGroupIndex("c", 2)
    assertThat(model.getGroupItems()).containsExactly("a", "b", "c")

    model.moveToGroupIndex("a", 1)
    assertThat(model.getGroupItems()).containsExactly("b", "a", "c")
    assertThat(model.getItems()).containsExactly("x", "b", "a", "c")
  }

  @Test
  fun `a move after the last tab puts the item at the end`() {
    val model = model("a", "b", "x", group = listOf("a", "b"))

    model.moveToGroupIndex("a", 5)

    assertThat(model.getItems()).containsExactly("b", "x", "a")
    assertThat(model.getGroupItems()).containsExactly("b", "a")
  }
}
