// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.intellij.util.containers.prefixTree.set.asSet
import com.intellij.util.containers.prefixTree.set.asMutableSet
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class PrefixTreeSetTest {

  private val TREE_FACTORY = PrefixTreeFactory.create<String, String> { it -> it.split("-") }

  @Test
  fun `test PrefixTreeSet#size`() {
    Assertions.assertThat(TREE_FACTORY.createSet())
      .hasSize(0)

    Assertions.assertThat(
      TREE_FACTORY.asSet(
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-c",
        "a"
      )
    ).hasSize(6)
  }

  @Test
  fun `test PrefixTreeSet#equals`() {
    Assertions.assertThat(TREE_FACTORY.createSet())
      .isEqualTo(emptySet<String>())

    Assertions.assertThat(
      TREE_FACTORY.asSet(
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-c",
        "a"
      )
    ).isEqualTo(
      setOf(
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-c",
        "a"
      )
    )
  }

  @Test
  fun `test PrefixTreeSet#containKey`() {
    val set = TREE_FACTORY.asSet(
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-c",
      "a"
    )
    Assertions.assertThat(set)
      .doesNotContain("a-b-c-1")
      .doesNotContain("a-b-c-2")
      .contains("a-b-c-3")
      .contains("a-b-c-4")
      .contains("a-b-1")
      .contains("a-b-2")
      .doesNotContain("a-b-3")
      .doesNotContain("a-b-4")
      .contains("a-b-c")
      .contains("a")
  }

  @Test
  fun `test PrefixTreeSet#keys`() {
    Assertions.assertThat(
      TREE_FACTORY.asSet(
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-c",
        "a"
      )
    ).containsOnly(
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-c",
      "a"
    )
  }

  @Test
  fun `test PrefixTreeSet#add`() {
    val set = TREE_FACTORY.asMutableSet(
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-c",
      "a"
    )

    set.add("a-b-c-1")
    set.add("a-b-c-2")
    set.add("a-b-3")
    set.add("a-b-4")

    Assertions.assertThat(set)
      .containsOnly(
        "a-b-c-1",
        "a-b-c-2",
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-3",
        "a-b-4",
        "a-b-c",
        "a"
      )
  }

  @Test
  fun `test PrefixTreeSet#remove`() {
    val set = TREE_FACTORY.asMutableSet(
      "a-b-c-1",
      "a-b-c-2",
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-3",
      "a-b-4",
      "a-b-c",
      "a"
    )

    set.remove("a-b-c-1")
    set.remove("a-b-c-2")
    set.remove("a-b-3")
    set.remove("a-b-4")

    Assertions.assertThat(set)
      .containsOnly(
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-c",
        "a"
      )
  }

  @Test
  fun `test set containing nullable values`() {
    val set = TREE_FACTORY.asMutableSet(
      "a-b-c-1",
      "a-b-c-2",
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-3",
      "a-b-4",
      "a-b-c",
      "a"
    )
    Assertions.assertThat(set)
      .containsOnly(
        "a-b-c-1",
        "a-b-c-2",
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-3",
        "a-b-4",
        "a-b-c",
        "a"
      )

    set.remove("a-b-c-1")
    set.remove("a-b-c-2")
    set.remove("a-b-3")
    set.remove("a-b-4")

    Assertions.assertThat(set)
      .containsOnly(
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-c",
        "a"
      )
  }

  @Test
  fun `test PrefixTreeSet#getDescendants`() {
    val set = TREE_FACTORY.asSet(
      "a-b-c-1",
      "a-b-c-2",
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-3",
      "a-b-4",
      "a-b-c",
      "a"
    )
    Assertions.assertThat(set.getDescendants("a-b-c"))
      .containsExactlyInAnyOrder(
        "a-b-c-1",
        "a-b-c-2",
        "a-b-c-3",
        "a-b-c-4",
        "a-b-c"
      )
    Assertions.assertThat(set.getDescendants("a-b"))
      .containsExactlyInAnyOrder(
        "a-b-c-1",
        "a-b-c-2",
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-3",
        "a-b-4",
        "a-b-c"
      )
    Assertions.assertThat(set.getDescendants("a"))
      .containsExactlyInAnyOrder(
        "a-b-c-1",
        "a-b-c-2",
        "a-b-c-3",
        "a-b-c-4",
        "a-b-1",
        "a-b-2",
        "a-b-3",
        "a-b-4",
        "a-b-c",
        "a"
      )
  }

  @Test
  fun `test PrefixTreeSet#getAncestors`() {
    val set = TREE_FACTORY.asSet(
      "a-b-c-1",
      "a-b-c-2",
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-3",
      "a-b-4",
      "a-b-c",
      "a"
    )
    Assertions.assertThat(set.getAncestors("a-b-c-1-loc"))
      .containsExactlyInAnyOrder(
        "a",
        "a-b-c",
        "a-b-c-1"
      )
    Assertions.assertThat(set.getAncestors("a-b-c-1"))
      .containsExactlyInAnyOrder(
        "a",
        "a-b-c",
        "a-b-c-1"
      )
    Assertions.assertThat(set.getAncestors("a-b-c"))
      .containsExactlyInAnyOrder(
        "a",
        "a-b-c"
      )
    Assertions.assertThat(set.getAncestors("a-b"))
      .containsExactlyInAnyOrder(
        "a"
      )
    Assertions.assertThat(set.getAncestors("a"))
      .containsExactlyInAnyOrder(
        "a"
      )
  }

  @Test
  fun `test PrefixTreeSet#getRoots`() {
    Assertions.assertThat(
      TREE_FACTORY.asSet(
        "a-b-c",
        "a-b-c-d",
        "a-b-c-e",
        "a-f-g"
      ).getRoots()
    ).containsExactlyInAnyOrder(
      "a-b-c",
      "a-f-g"
    )
    Assertions.assertThat(
      TREE_FACTORY.asSet(
        "a-b",
        "a-b-c",
        "a-b-c-d",
        "a-b-c-e",
        "a-f-g"
      ).getRoots()
    ).containsExactlyInAnyOrder(
      "a-b",
      "a-f-g"
    )
    Assertions.assertThat(
      TREE_FACTORY.asSet(
        "a",
        "a-b",
        "a-b-c",
        "a-b-c-d",
        "a-b-c-e",
        "a-f-g"
      ).getRoots()
    ).containsExactlyInAnyOrder(
      "a"
    )
  }
}