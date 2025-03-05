// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import org.assertj.core.api.Assertions.entry
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class PrefixTreeTest {

  @Test
  fun `test PrefixTree#size`() {
    Assertions.assertThat(emptyPrefixTree<String, Int>())
      .hasSize(0)

    Assertions.assertThat(
      prefixTreeOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      )
    ).hasSize(6)
  }

  @Test
  fun `test PrefixTree#equals`() {
    Assertions.assertThat(emptyPrefixTree<String, Int>())
      .isEqualTo(emptyMap<List<String>, Int>())

    Assertions.assertThat(
      prefixTreeOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      )
    ).isEqualTo(
      mapOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      )
    )
  }

  @Test
  fun `test PrefixTree#get`() {
    val tree = prefixTreeOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "1")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "2")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "3")]).isEqualTo(30)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "4")]).isEqualTo(10)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "1")]).isEqualTo(11)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "2")]).isEqualTo(21)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "3")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "4")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c")]).isEqualTo(43)
    Assertions.assertThat<Int>(tree[listOf("a")]).isEqualTo(13)
  }

  @Test
  fun `test PrefixTree#containKey`() {
    val tree = prefixTreeOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree)
      .doesNotContainKey(listOf("a", "b", "c", "1"))
      .doesNotContainKey(listOf("a", "b", "c", "2"))
      .containsKey(listOf("a", "b", "c", "3"))
      .containsKey(listOf("a", "b", "c", "4"))
      .containsKey(listOf("a", "b", "1"))
      .containsKey(listOf("a", "b", "2"))
      .doesNotContainKey(listOf("a", "b", "3"))
      .doesNotContainKey(listOf("a", "b", "4"))
      .containsKey(listOf("a", "b", "c"))
      .containsKey(listOf("a"))
  }

  @Test
  fun `test PrefixTree#containValue`() {
    val tree = prefixTreeOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 40,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree)
      .containsValue(30)
      .containsValue(40)
      .containsValue(11)
      .containsValue(21)
      .containsValue(43)
      .containsValue(13)
      .doesNotContainValue(0)
      .doesNotContainValue(10)
  }

  @Test
  fun `test PrefixTree#keys`() {
    Assertions.assertThat(
      prefixTreeOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      )
    ).containsOnlyKeys(
      listOf("a", "b", "c", "3"),
      listOf("a", "b", "c", "4"),
      listOf("a", "b", "1"),
      listOf("a", "b", "2"),
      listOf("a", "b", "c"),
      listOf("a")
    )
  }

  @Test
  fun `test PrefixTree#values`() {
    val tree = prefixTreeOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree).values()
      .containsExactlyInAnyOrder(30, 10, 11, 21, 43, 13)
  }

  @Test
  fun `test PrefixTree#put`() {
    val tree = mutablePrefixTreeOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )

    tree[listOf("a", "b", "c", "1")] = 10
    tree[listOf("a", "b", "c", "2")] = 20
    tree[listOf("a", "b", "3")] = 30
    tree[listOf("a", "b", "4")] = 11

    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "1"), 10),
        entry(listOf("a", "b", "c", "2"), 20),
        entry(listOf("a", "b", "c", "3"), 30),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), 11),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "3"), 30),
        entry(listOf("a", "b", "4"), 11),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )
  }

  @Test
  fun `test PrefixTree#remove`() {
    val tree = mutablePrefixTreeOf(
      listOf("a", "b", "c", "1") to 10,
      listOf("a", "b", "c", "2") to 20,
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "3") to 30,
      listOf("a", "b", "4") to 11,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )

    tree.remove(listOf("a", "b", "c", "1"))
    tree.remove(listOf("a", "b", "c", "2"))
    tree.remove(listOf("a", "b", "3"))
    tree.remove(listOf("a", "b", "4"))

    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "3"), 30),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), 11),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )
  }

  @Test
  fun `test map containing nullable values`() {
    val tree = mutablePrefixTreeOf(
      listOf("a", "b", "c", "1") to null,
      listOf("a", "b", "c", "2") to 20,
      listOf("a", "b", "c", "3") to null,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to null,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "3") to null,
      listOf("a", "b", "4") to 11,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "1"), null),
        entry(listOf("a", "b", "c", "2"), 20),
        entry(listOf("a", "b", "c", "3"), null),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), null),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "3"), null),
        entry(listOf("a", "b", "4"), 11),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )

    tree.remove(listOf("a", "b", "c", "1"))
    tree.remove(listOf("a", "b", "c", "2"))
    tree.remove(listOf("a", "b", "3"))
    tree.remove(listOf("a", "b", "4"))

    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "3"), null),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), null),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )
  }

  @Test
  fun `test PrefixTree#getDescendantEntries`() {
    val tree = prefixTreeOf(
      listOf("a", "b", "c", "1") to 1,
      listOf("a", "b", "c", "2") to 2,
      listOf("a", "b", "c", "3") to 3,
      listOf("a", "b", "c", "4") to 4,
      listOf("a", "b", "1") to 1,
      listOf("a", "b", "2") to 2,
      listOf("a", "b", "3") to 3,
      listOf("a", "b", "4") to 4,
      listOf("a", "b", "c") to 5,
      listOf("a") to 1
    )
    Assertions.assertThat(tree.getDescendantEntries(listOf("a", "b", "c")))
      .containsExactlyInAnyOrder(
        entry(listOf("a", "b", "c", "1"), 1),
        entry(listOf("a", "b", "c", "2"), 2),
        entry(listOf("a", "b", "c", "3"), 3),
        entry(listOf("a", "b", "c", "4"), 4),
        entry(listOf("a", "b", "c"), 5)
      )
    Assertions.assertThat(tree.getDescendantEntries(listOf("a", "b")))
      .containsExactlyInAnyOrder(
        entry(listOf("a", "b", "c", "1"), 1),
        entry(listOf("a", "b", "c", "2"), 2),
        entry(listOf("a", "b", "c", "3"), 3),
        entry(listOf("a", "b", "c", "4"), 4),
        entry(listOf("a", "b", "1"), 1),
        entry(listOf("a", "b", "2"), 2),
        entry(listOf("a", "b", "3"), 3),
        entry(listOf("a", "b", "4"), 4),
        entry(listOf("a", "b", "c"), 5)
      )
    Assertions.assertThat(tree.getDescendantEntries(listOf("a")))
      .containsExactlyInAnyOrder(
        entry(listOf("a", "b", "c", "1"), 1),
        entry(listOf("a", "b", "c", "2"), 2),
        entry(listOf("a", "b", "c", "3"), 3),
        entry(listOf("a", "b", "c", "4"), 4),
        entry(listOf("a", "b", "1"), 1),
        entry(listOf("a", "b", "2"), 2),
        entry(listOf("a", "b", "3"), 3),
        entry(listOf("a", "b", "4"), 4),
        entry(listOf("a", "b", "c"), 5),
        entry(listOf("a"), 1)
      )
    Assertions.assertThat(tree.getDescendantEntries(emptyList()))
      .containsExactlyInAnyOrder(
        entry(listOf("a", "b", "c", "1"), 1),
        entry(listOf("a", "b", "c", "2"), 2),
        entry(listOf("a", "b", "c", "3"), 3),
        entry(listOf("a", "b", "c", "4"), 4),
        entry(listOf("a", "b", "1"), 1),
        entry(listOf("a", "b", "2"), 2),
        entry(listOf("a", "b", "3"), 3),
        entry(listOf("a", "b", "4"), 4),
        entry(listOf("a", "b", "c"), 5),
        entry(listOf("a"), 1)
      )
  }

  @Test
  fun `test PrefixTree#getAncestorEntries`() {
    val tree = prefixTreeOf(
      listOf("a", "b", "c", "1") to 1,
      listOf("a", "b", "c", "2") to 2,
      listOf("a", "b", "c", "3") to 3,
      listOf("a", "b", "c", "4") to 4,
      listOf("a", "b", "1") to 1,
      listOf("a", "b", "2") to 2,
      listOf("a", "b", "3") to 3,
      listOf("a", "b", "4") to 4,
      listOf("a", "b", "c") to 5,
      listOf("a") to 1
    )
    Assertions.assertThat(tree.getAncestorEntries(listOf("a", "b", "c", "1", "loc")))
      .containsExactlyInAnyOrder(
        entry(listOf("a"), 1),
        entry(listOf("a", "b", "c"), 5),
        entry(listOf("a", "b", "c", "1"), 1)
      )
    Assertions.assertThat(tree.getAncestorEntries(listOf("a", "b", "c", "1")))
      .containsExactlyInAnyOrder(
        entry(listOf("a"), 1),
        entry(listOf("a", "b", "c"), 5),
        entry(listOf("a", "b", "c", "1"), 1)
      )
    Assertions.assertThat(tree.getAncestorEntries(listOf("a", "b", "c")))
      .containsExactlyInAnyOrder(
        entry(listOf("a"), 1),
        entry(listOf("a", "b", "c"), 5)
      )
    Assertions.assertThat(tree.getAncestorEntries(listOf("a", "b")))
      .containsExactlyInAnyOrder(
        entry(listOf("a"), 1)
      )
    Assertions.assertThat(tree.getAncestorEntries(listOf("a")))
      .containsExactlyInAnyOrder(
        entry(listOf("a"), 1)
      )
    Assertions.assertThat(tree.getAncestorEntries(emptyList()))
      .isEmpty()
  }

  @Test
  fun `test PrefixTree#getRootEntries`() {
    Assertions.assertThat(
      prefixTreeOf(
        listOf("a", "b", "c") to 1,
        listOf("a", "b", "c", "d") to 1,
        listOf("a", "b", "c", "e") to 2,
        listOf("a", "f", "g") to 1
      ).getRootEntries()
    ).containsExactlyInAnyOrder(
      entry(listOf("a", "b", "c"), 1),
      entry(listOf("a", "f", "g"), 1)
    )
    Assertions.assertThat(
      prefixTreeOf(
        listOf("a", "b") to 1,
        listOf("a", "b", "c") to 1,
        listOf("a", "b", "c", "d") to 1,
        listOf("a", "b", "c", "e") to 2,
        listOf("a", "f", "g") to 1
      ).getRootEntries()
    ).containsExactlyInAnyOrder(
      entry(listOf("a", "b"), 1),
      entry(listOf("a", "f", "g"), 1)
    )
    Assertions.assertThat(
      prefixTreeOf(
        listOf("a") to 1,
        listOf("a", "b") to 1,
        listOf("a", "b", "c") to 1,
        listOf("a", "b", "c", "d") to 1,
        listOf("a", "b", "c", "e") to 2,
        listOf("a", "f", "g") to 1
      ).getRootEntries()
    ).containsExactlyInAnyOrder(
      entry(listOf("a"), 1)
    )
    Assertions.assertThat(
      prefixTreeOf(
        emptyList<String>() to 1,
        listOf("a") to 1,
        listOf("a", "b") to 1,
        listOf("a", "b", "c") to 1,
        listOf("a", "b", "c", "d") to 1,
        listOf("a", "b", "c", "e") to 2,
        listOf("a", "f", "g") to 1
      ).getRootEntries()
    ).containsExactlyInAnyOrder(
      entry(emptyList(), 1)
    )
  }
}