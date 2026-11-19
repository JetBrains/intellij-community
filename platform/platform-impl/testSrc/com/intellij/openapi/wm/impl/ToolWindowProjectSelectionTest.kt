// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The project selection of a tool window, without a frame. A project is a string here. */
internal class ToolWindowProjectSelectionTest {
  private var active: String? = null
  private val selection = ToolWindowProjectSelection { active }

  @Test
  fun `no entry is selected before the first resolve`() {
    active = "first"
    selection.add("first")

    assertThat(selection.selected).isNull()
  }

  @Test
  fun `the first resolve selects the entry of the active project`() {
    selection.add("first")
    selection.add("second")
    active = "second"

    assertThat(selection.resolve()).isEqualTo("second")
    assertThat(selection.selected).isEqualTo("second")
  }

  @Test
  fun `a resolve without entries selects nothing`() {
    active = "first"

    assertThat(selection.resolve()).isNull()
  }

  @Test
  fun `the selection does not follow the active project`() {
    selection.add("first")
    selection.add("second")
    active = "first"
    selection.resolve()

    active = "second"

    assertThat(selection.resolve()).isEqualTo("first")
    assertThat(selection.add("third")).isFalse()
    assertThat(selection.selected).isEqualTo("first")
  }

  @Test
  fun `two selections are independent`() {
    val other = ToolWindowProjectSelection { active }
    for (entry in listOf("first", "second")) {
      selection.add(entry)
      other.add(entry)
    }
    active = "first"
    selection.resolve()
    other.resolve()

    selection.select("second")

    assertThat(selection.selected).isEqualTo("second")
    assertThat(other.selected).isEqualTo("first")
  }

  @Test
  fun `a provisional selection moves once to the active project`() {
    active = "third"
    selection.add("first")
    assertThat(selection.resolve()).describedAs("the active project has no entry").isEqualTo("first")

    assertThat(selection.add("second")).describedAs("the active project still has no entry").isFalse()
    assertThat(selection.add("third")).isTrue()
    assertThat(selection.selected).isEqualTo("third")

    active = "second"
    selection.remove("first")
    selection.add("first")
    assertThat(selection.selected).describedAs("the selection is not provisional any more").isEqualTo("third")
  }

  @Test
  fun `a provisional selection that is the active project stays`() {
    selection.add("first")
    selection.resolve()
    active = "first"

    assertThat(selection.add("second")).isFalse()
    assertThat(selection.selected).isEqualTo("first")
  }

  @Test
  fun `a select ends a provisional selection`() {
    active = "third"
    selection.add("first")
    selection.add("second")
    selection.resolve()

    assertThat(selection.select("second")).isTrue()
    assertThat(selection.add("third")).isFalse()
    assertThat(selection.selected).isEqualTo("second")
  }

  @Test
  fun `a select of the selection changes nothing`() {
    selection.add("first")
    active = "first"
    selection.resolve()

    assertThat(selection.select("first")).isFalse()
  }

  @Test
  fun `a select of a project without an entry fails`() {
    assertThatThrownBy { selection.select("first") }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `the removal of the selection selects nothing until the next resolve`() {
    selection.add("first")
    selection.add("second")
    active = "second"
    selection.resolve()

    assertThat(selection.remove("second")).isTrue()
    assertThat(selection.selected).isNull()

    active = "first"
    assertThat(selection.resolve()).isEqualTo("first")
  }

  @Test
  fun `the removal of another entry keeps the selection`() {
    selection.add("first")
    selection.add("second")
    active = "second"
    selection.resolve()

    assertThat(selection.remove("first")).isFalse()
    assertThat(selection.selected).isEqualTo("second")
    assertThat(selection.entries).containsExactly("second")
  }

  @Test
  fun `the frame owner goes before the frame guests`() {
    selection.add("guest")
    selection.add("owner", first = true)
    selection.add("guest")

    assertThat(selection.entries).containsExactly("owner", "guest")
  }
}
