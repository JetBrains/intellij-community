// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.intellij.util.containers.prefixTree.map.asMap
import com.intellij.util.containers.prefixTree.map.asMutableMap
import org.assertj.core.api.Assertions.entry
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class PrefixTreeMapTest {

  private val TREE_FACTORY = PrefixTreeFactory.create<String, String> { it -> it.split("-") }

  @Test
  fun `test PrefixTreeMap#size`() {
    Assertions.assertThat(TREE_FACTORY.createMap<Int>())
      .hasSize(0)

    Assertions.assertThat(
      TREE_FACTORY.asMap(
        "a-b-c-3" to 30,
        "a-b-c-4" to 10,
        "a-b-1" to 11,
        "a-b-2" to 21,
        "a-b-c" to 43,
        "a" to 13
      )
    ).hasSize(6)
  }

  @Test
  fun `test PrefixTreeMap#equals`() {
    Assertions.assertThat(TREE_FACTORY.createMap<Int>())
      .isEqualTo(emptyMap<String, Int>())

    Assertions.assertThat(
      TREE_FACTORY.asMap(
        "a-b-c-3" to 30,
        "a-b-c-4" to 10,
        "a-b-1" to 11,
        "a-b-2" to 21,
        "a-b-c" to 43,
        "a" to 13
      )
    ).isEqualTo(
      mapOf(
        "a-b-c-3" to 30,
        "a-b-c-4" to 10,
        "a-b-1" to 11,
        "a-b-2" to 21,
        "a-b-c" to 43,
        "a" to 13
      )
    )
  }

  @Test
  fun `test PrefixTreeMap#get`() {
    val map = TREE_FACTORY.asMap(
      "a-b-c-3" to 30,
      "a-b-c-4" to 10,
      "a-b-1" to 11,
      "a-b-2" to 21,
      "a-b-c" to 43,
      "a" to 13
    )
    Assertions.assertThat<Int>(map["a-b-c-1"]).isEqualTo(null)
    Assertions.assertThat<Int>(map["a-b-c-2"]).isEqualTo(null)
    Assertions.assertThat<Int>(map["a-b-c-3"]).isEqualTo(30)
    Assertions.assertThat<Int>(map["a-b-c-4"]).isEqualTo(10)
    Assertions.assertThat<Int>(map["a-b-1"]).isEqualTo(11)
    Assertions.assertThat<Int>(map["a-b-2"]).isEqualTo(21)
    Assertions.assertThat<Int>(map["a-b-3"]).isEqualTo(null)
    Assertions.assertThat<Int>(map["a-b-4"]).isEqualTo(null)
    Assertions.assertThat<Int>(map["a-b-c"]).isEqualTo(43)
    Assertions.assertThat<Int>(map["a"]).isEqualTo(13)
  }

  @Test
  fun `test PrefixTreeMap#containKey`() {
    val map = TREE_FACTORY.asMap(
      "a-b-c-3" to 30,
      "a-b-c-4" to 10,
      "a-b-1" to 11,
      "a-b-2" to 21,
      "a-b-c" to 43,
      "a" to 13
    )
    Assertions.assertThat(map)
      .doesNotContainKey("a-b-c-1")
      .doesNotContainKey("a-b-c-2")
      .containsKey("a-b-c-3")
      .containsKey("a-b-c-4")
      .containsKey("a-b-1")
      .containsKey("a-b-2")
      .doesNotContainKey("a-b-3")
      .doesNotContainKey("a-b-4")
      .containsKey("a-b-c")
      .containsKey("a")
  }

  @Test
  fun `test PrefixTreeMap#containValue`() {
    val map = TREE_FACTORY.asMap(
      "a-b-c-3" to 30,
      "a-b-c-4" to 40,
      "a-b-1" to 11,
      "a-b-2" to 21,
      "a-b-c" to 43,
      "a" to 13
    )
    Assertions.assertThat(map)
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
  fun `test PrefixTreeMap#keys`() {
    Assertions.assertThat(
      TREE_FACTORY.asMap(
        "a-b-c-3" to 30,
        "a-b-c-4" to 10,
        "a-b-1" to 11,
        "a-b-2" to 21,
        "a-b-c" to 43,
        "a" to 13
      )
    ).containsOnlyKeys(
      "a-b-c-3",
      "a-b-c-4",
      "a-b-1",
      "a-b-2",
      "a-b-c",
      "a"
    )
  }

  @Test
  fun `test PrefixTreeMap#values`() {
    val map = TREE_FACTORY.asMap(
      "a-b-c-3" to 30,
      "a-b-c-4" to 10,
      "a-b-1" to 11,
      "a-b-2" to 21,
      "a-b-c" to 43,
      "a" to 13
    )
    Assertions.assertThat(map).values()
      .containsExactlyInAnyOrder(30, 10, 11, 21, 43, 13)
  }

  @Test
  fun `test PrefixTreeMap#put`() {
    val map = TREE_FACTORY.asMutableMap(
      "a-b-c-3" to 30,
      "a-b-c-4" to 10,
      "a-b-1" to 11,
      "a-b-2" to 21,
      "a-b-c" to 43,
      "a" to 13
    )

    map["a-b-c-1"] = 10
    map["a-b-c-2"] = 20
    map["a-b-3"] = 30
    map["a-b-4"] = 11

    Assertions.assertThat(map)
      .containsOnly(
        entry("a-b-c-1", 10),
        entry("a-b-c-2", 20),
        entry("a-b-c-3", 30),
        entry("a-b-c-4", 10),
        entry("a-b-1", 11),
        entry("a-b-2", 21),
        entry("a-b-3", 30),
        entry("a-b-4", 11),
        entry("a-b-c", 43),
        entry("a", 13)
      )
  }

  @Test
  fun `test PrefixTreeMap#remove`() {
    val map = TREE_FACTORY.asMutableMap(
      "a-b-c-1" to 10,
      "a-b-c-2" to 20,
      "a-b-c-3" to 30,
      "a-b-c-4" to 10,
      "a-b-1" to 11,
      "a-b-2" to 21,
      "a-b-3" to 30,
      "a-b-4" to 11,
      "a-b-c" to 43,
      "a" to 13
    )

    map.remove("a-b-c-1")
    map.remove("a-b-c-2")
    map.remove("a-b-3")
    map.remove("a-b-4")

    Assertions.assertThat(map)
      .containsOnly(
        entry("a-b-c-3", 30),
        entry("a-b-c-4", 10),
        entry("a-b-1", 11),
        entry("a-b-2", 21),
        entry("a-b-c", 43),
        entry("a", 13)
      )
  }

  @Test
  fun `test map containing nullable values`() {
    val map = TREE_FACTORY.asMutableMap(
      "a-b-c-1" to null,
      "a-b-c-2" to 20,
      "a-b-c-3" to null,
      "a-b-c-4" to 10,
      "a-b-1" to null,
      "a-b-2" to 21,
      "a-b-3" to null,
      "a-b-4" to 11,
      "a-b-c" to 43,
      "a" to 13
    )
    Assertions.assertThat(map)
      .containsOnly(
        entry("a-b-c-1", null),
        entry("a-b-c-2", 20),
        entry("a-b-c-3", null),
        entry("a-b-c-4", 10),
        entry("a-b-1", null),
        entry("a-b-2", 21),
        entry("a-b-3", null),
        entry("a-b-4", 11),
        entry("a-b-c", 43),
        entry("a", 13)
      )

    map.remove("a-b-c-1")
    map.remove("a-b-c-2")
    map.remove("a-b-3")
    map.remove("a-b-4")

    Assertions.assertThat(map)
      .containsOnly(
        entry("a-b-c-3", null),
        entry("a-b-c-4", 10),
        entry("a-b-1", null),
        entry("a-b-2", 21),
        entry("a-b-c", 43),
        entry("a", 13)
      )
  }

  @Test
  fun `test PrefixTreeMap#getDescendantEntries`() {
    val map = TREE_FACTORY.asMap(
      "a-b-c-1" to 1,
      "a-b-c-2" to 2,
      "a-b-c-3" to 3,
      "a-b-c-4" to 4,
      "a-b-1" to 1,
      "a-b-2" to 2,
      "a-b-3" to 3,
      "a-b-4" to 4,
      "a-b-c" to 5,
      "a" to 1
    )
    Assertions.assertThat(map.getDescendantEntries("a-b-c"))
      .containsExactlyInAnyOrder(
        entry("a-b-c-1", 1),
        entry("a-b-c-2", 2),
        entry("a-b-c-3", 3),
        entry("a-b-c-4", 4),
        entry("a-b-c", 5)
      )
    Assertions.assertThat(map.getDescendantEntries("a-b"))
      .containsExactlyInAnyOrder(
        entry("a-b-c-1", 1),
        entry("a-b-c-2", 2),
        entry("a-b-c-3", 3),
        entry("a-b-c-4", 4),
        entry("a-b-1", 1),
        entry("a-b-2", 2),
        entry("a-b-3", 3),
        entry("a-b-4", 4),
        entry("a-b-c", 5)
      )
    Assertions.assertThat(map.getDescendantEntries("a"))
      .containsExactlyInAnyOrder(
        entry("a-b-c-1", 1),
        entry("a-b-c-2", 2),
        entry("a-b-c-3", 3),
        entry("a-b-c-4", 4),
        entry("a-b-1", 1),
        entry("a-b-2", 2),
        entry("a-b-3", 3),
        entry("a-b-4", 4),
        entry("a-b-c", 5),
        entry("a", 1)
      )
  }

  @Test
  fun `test PrefixTreeMap#getAncestorEntries`() {
    val map = TREE_FACTORY.asMap(
      "a-b-c-1" to 1,
      "a-b-c-2" to 2,
      "a-b-c-3" to 3,
      "a-b-c-4" to 4,
      "a-b-1" to 1,
      "a-b-2" to 2,
      "a-b-3" to 3,
      "a-b-4" to 4,
      "a-b-c" to 5,
      "a" to 1
    )
    Assertions.assertThat(map.getAncestorEntries("a-b-c-1-loc"))
      .containsExactlyInAnyOrder(
        entry("a", 1),
        entry("a-b-c", 5),
        entry("a-b-c-1", 1)
      )
    Assertions.assertThat(map.getAncestorEntries("a-b-c-1"))
      .containsExactlyInAnyOrder(
        entry("a", 1),
        entry("a-b-c", 5),
        entry("a-b-c-1", 1)
      )
    Assertions.assertThat(map.getAncestorEntries("a-b-c"))
      .containsExactlyInAnyOrder(
        entry("a", 1),
        entry("a-b-c", 5)
      )
    Assertions.assertThat(map.getAncestorEntries("a-b"))
      .containsExactlyInAnyOrder(
        entry("a", 1)
      )
    Assertions.assertThat(map.getAncestorEntries("a"))
      .containsExactlyInAnyOrder(
        entry("a", 1)
      )
  }

  @Test
  fun `test PrefixTreeMap#getRootEntries`() {
    Assertions.assertThat(
      TREE_FACTORY.asMap(
        "a-b-c" to 1,
        "a-b-c-d" to 1,
        "a-b-c-e" to 2,
        "a-f-g" to 1
      ).getRootEntries()
    ).containsExactlyInAnyOrder(
      entry("a-b-c", 1),
      entry("a-f-g", 1)
    )
    Assertions.assertThat(
      TREE_FACTORY.asMap(
        "a-b" to 1,
        "a-b-c" to 1,
        "a-b-c-d" to 1,
        "a-b-c-e" to 2,
        "a-f-g" to 1
      ).getRootEntries()
    ).containsExactlyInAnyOrder(
      entry("a-b", 1),
      entry("a-f-g", 1)
    )
    Assertions.assertThat(
      TREE_FACTORY.asMap(
        "a" to 1,
        "a-b" to 1,
        "a-b-c" to 1,
        "a-b-c-d" to 1,
        "a-b-c-e" to 2,
        "a-f-g" to 1
      ).getRootEntries()
    ).containsExactlyInAnyOrder(
      entry("a", 1)
    )
  }
}