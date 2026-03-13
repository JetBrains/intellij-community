// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.ordering

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyNamedEntity
import com.intellij.platform.workspace.storage.tests.from
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals

class ChildrenOrderingTest {
  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `order of children is saved`(state: EntityState) {
    val newEntity = NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
        NamedChildEntity("Three", MySource),
      )
    }
    val builder = MutableEntityStorage.create().also { it addEntity newEntity }
    val entity = when (state) {
      EntityState.FROM_IMMUTABLE -> builder.toSnapshot().entities<NamedEntity>().single()
      EntityState.FROM_BUILDER -> builder.entities<NamedEntity>().single()
    }

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("Three", entity.children[2].childProperty)
  }

  @Test
  fun `order of children is saved in entity without adding  to storage`() {
    val entity = NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
        NamedChildEntity("Three", MySource),
      )
    }

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("Three", entity.children[2].childProperty)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `check that order of children is preserved after reversing`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
        NamedChildEntity("Three", MySource),
      )
    }

    builder.modifyNamedEntity(entity) {
      this.children = listOf(this.children[2], this.children[1], this.children[0])
    }
    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder.toSnapshot().entities<NamedEntity>().single()
      EntityState.FROM_BUILDER -> entity
    }

    assertEquals("Three", newEntity.children[0].childProperty)
    assertEquals("Two", newEntity.children[1].childProperty)
    assertEquals("One", newEntity.children[2].childProperty)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `removing first and adding last has proper ordering`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
        NamedChildEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2.removeEntity(entity.children.first().from(builder2))
    builder2.modifyNamedEntity(entity.from(builder2)) {
      this.children += NamedChildEntity("Four", MySource)
    }

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<NamedEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<NamedEntity>().single()
    }

    assertEquals("Two", newEntity.children[0].childProperty)
    assertEquals("Three", newEntity.children[1].childProperty)
    assertEquals("Four", newEntity.children[2].childProperty)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `removing second entity keeps proper ordering`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
        NamedChildEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2.removeEntity(entity.children[1].from(builder2))

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<NamedEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<NamedEntity>().single()
    }

    assertEquals("One", newEntity.children[0].childProperty)
    assertEquals("Three", newEntity.children[1].childProperty)
  }

  enum class EntityState {
    FROM_IMMUTABLE,
    FROM_BUILDER,
  }
}