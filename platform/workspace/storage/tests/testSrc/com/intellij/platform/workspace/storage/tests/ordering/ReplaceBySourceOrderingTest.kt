// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.ordering

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReplaceBySourceOrderingTest {
  @Test
  fun `new children go the end`() {
    val targetStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", MySource)
    }.toSnapshot()

    val secondStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", MySource) {
        this.children = listOf(
          NamedChildEntity("1", MySource),
          NamedChildEntity("2", MySource),
          NamedChildEntity("3", MySource),
        )
      }
    }.toSnapshot()

    val resultBuilder = targetStorage.toBuilder()
    resultBuilder.replaceBySource({ true }, secondStorage)

    val children = resultBuilder.entities<NamedEntity>().single().children
    assertEquals("1", children[0].childProperty)
    assertEquals("2", children[1].childProperty)
    assertEquals("3", children[2].childProperty)
  }

  @Test
  fun `order of old children remains when parent is replaced`() {
    val targetStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", MySource) {
        this.children = listOf(
          NamedChildEntity("1", AnotherSource),
          NamedChildEntity("2", AnotherSource),
          NamedChildEntity("3", AnotherSource),
        )
      }
    }.toSnapshot()

    val secondStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", MySource)
    }.toSnapshot()

    val resultBuilder = targetStorage.toBuilder()
    resultBuilder.replaceBySource({ it is MySource }, secondStorage)

    val children = resultBuilder.entities<NamedEntity>().single().children
    assertEquals("1", children[0].childProperty)
    assertEquals("2", children[1].childProperty)
    assertEquals("3", children[2].childProperty)
  }

  @Test
  fun `order of all children remains when only children are replaced`() {
    val targetStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", AnotherSource) {
        this.children = listOf(
          NamedChildEntity("1", MySource),
          NamedChildEntity("2", MySource),
          NamedChildEntity("3", MySource),
        )
      }
    }.toSnapshot()

    val secondStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", AnotherSource) {
        this.children = listOf(
          NamedChildEntity("10", MySource),
          NamedChildEntity("20", MySource),
          NamedChildEntity("30", MySource),
        )
      }
    }.toSnapshot()

    val resultBuilder = targetStorage.toBuilder()
    resultBuilder.replaceBySource({ it is MySource }, secondStorage)

    val children = resultBuilder.entities<NamedEntity>().single().children
    assertEquals("10", children[0].childProperty)
    assertEquals("20", children[1].childProperty)
    assertEquals("30", children[2].childProperty)
  }

  @Test
  fun `order of all children remains when some children and parent are replaced`() {
    val targetStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", MySource) {
        this.children = listOf(
          NamedChildEntity("1", MySource),
          NamedChildEntity("2", AnotherSource),
          NamedChildEntity("3", MySource),
        )
      }
    }.toSnapshot()

    val secondStorage = MutableEntityStorage.create().also {
      it addEntity NamedEntity("MyName", MySource) {
        this.children = listOf(
          NamedChildEntity("10", AnotherSource),
          NamedChildEntity("20", MySource),
          NamedChildEntity("30", MySource),
        )
      }
    }.toSnapshot()

    val resultBuilder = targetStorage.toBuilder()
    resultBuilder.replaceBySource({ it is MySource }, secondStorage)

    val children = resultBuilder.entities<NamedEntity>().single().children
    assertEquals("2", children[0].childProperty)
    assertEquals("20", children[1].childProperty)
    assertEquals("30", children[2].childProperty)
  }
}
