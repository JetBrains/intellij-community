// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.ordering

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.tests.builderFrom
import com.intellij.platform.workspace.storage.tests.from
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.util.asSafely
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals

class AbstractChildrenOrderingTest {
  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `adda abstract one child in front`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
        NamedChildEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2.modifyNamedEntity(entity.from(builder2)) {
      this.children = listOf(NamedChildEntity("Zero", MySource)) + this.children
    }

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<NamedEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<NamedEntity>().single()
    }

    assertEquals("Zero", newEntity.children[0].childProperty)
    assertEquals("One", newEntity.children[1].childProperty)
    assertEquals("Two", newEntity.children[2].childProperty)
    assertEquals("Three", newEntity.children[3].childProperty)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `abstract child is added to the end`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity NamedEntity("123", MySource) {
      this.children = listOf(
        NamedChildEntity("One", MySource),
        NamedChildEntity("Two", MySource),
        NamedChildEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2 addEntity NamedChildEntity("Four", MySource) {
      this.parentEntity = entity.builderFrom(builder2)
    }

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<NamedEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<NamedEntity>().single()
    }

    assertEquals("One", newEntity.children[0].childProperty)
    assertEquals("Two", newEntity.children[1].childProperty)
    assertEquals("Three", newEntity.children[2].childProperty)
    assertEquals("Four", newEntity.children[3].childProperty)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `order of abstract children is saved`(state: EntityState) {
    val newEntity = LeftEntity(MySource) {
      this.children = listOf(
        MiddleEntity("One", MySource),
        MiddleEntity("Two", MySource),
        MiddleEntity("Three", MySource),
      )
    }
    val builder = MutableEntityStorage.create().also { it addEntity newEntity }
    val entity = when (state) {
      EntityState.FROM_IMMUTABLE -> builder.toSnapshot().entities<LeftEntity>().single()
      EntityState.FROM_BUILDER -> builder.entities<LeftEntity>().single()
    }

    assertEquals("One", entity.children[0].asSafely<MiddleEntity>()!!.property)
    assertEquals("Two", entity.children[1].asSafely<MiddleEntity>()!!.property)
    assertEquals("Three", entity.children[2].asSafely<MiddleEntity>()!!.property)
  }

  @Test
  fun `order of abstract children is saved in entity without adding to storage`() {
    val entity = LeftEntity(MySource) {
      this.children = listOf(
        MiddleEntity("One", MySource),
        MiddleEntity("Two", MySource),
        MiddleEntity("Three", MySource),
      )
    }

    assertEquals("One", entity.children[0].asSafely<MiddleEntityBuilder>()!!.property)
    assertEquals("Two", entity.children[1].asSafely<MiddleEntityBuilder>()!!.property)
    assertEquals("Three", entity.children[2].asSafely<MiddleEntityBuilder>()!!.property)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `check that order of abstract children is preserved after reversing`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity LeftEntity(MySource) {
      this.children = listOf(
        MiddleEntity("One", MySource),
        MiddleEntity("Two", MySource),
        MiddleEntity("Three", MySource),
      )
    }

    builder.modifyLeftEntity(entity) {
      this.children = listOf(this.children[2], this.children[1], this.children[0])
    }
    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder.toSnapshot().entities<LeftEntity>().single()
      EntityState.FROM_BUILDER -> entity
    }

    assertEquals("Three", newEntity.children[0].asSafely<MiddleEntity>()!!.property)
    assertEquals("Two", newEntity.children[1].asSafely<MiddleEntity>()!!.property)
    assertEquals("One", newEntity.children[2].asSafely<MiddleEntity>()!!.property)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `removing abstract first and adding last has proper ordering`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity LeftEntity(MySource) {
      this.children = listOf(
        MiddleEntity("One", MySource),
        MiddleEntity("Two", MySource),
        MiddleEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2.removeEntity(entity.children.first().from(builder2))
    builder2.modifyLeftEntity(entity.from(builder2)) {
      this.children += MiddleEntity("Four", MySource)
    }

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<LeftEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<LeftEntity>().single()
    }

    assertEquals("Two", newEntity.children[0].asSafely<MiddleEntity>()!!.property)
    assertEquals("Three", newEntity.children[1].asSafely<MiddleEntity>()!!.property)
    assertEquals("Four", newEntity.children[2].asSafely<MiddleEntity>()!!.property)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `removing abstract second entity keeps proper ordering`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val root = builder addEntity LeftEntity(MySource) {
      this.children = listOf(
        MiddleEntity("One", MySource),
        MiddleEntity("Two", MySource),
        MiddleEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2.removeEntity(root.children[1].from(builder2))

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<LeftEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<LeftEntity>().single()
    }

    assertEquals("One", newEntity.children[0].asSafely<MiddleEntity>()!!.property)
    assertEquals("Three", newEntity.children[1].asSafely<MiddleEntity>()!!.property)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `add one child in front `(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity LeftEntity(MySource) {
      this.children = listOf(
        MiddleEntity("One", MySource),
        MiddleEntity("Two", MySource),
        MiddleEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2.modifyLeftEntity(entity.from(builder2)) {
      this.children = listOf(MiddleEntity("Zero", MySource)) + this.children
    }

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<LeftEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<LeftEntity>().single()
    }

    assertEquals("Zero", newEntity.children[0].asSafely<MiddleEntity>()!!.property)
    assertEquals("One", newEntity.children[1].asSafely<MiddleEntity>()!!.property)
    assertEquals("Two", newEntity.children[2].asSafely<MiddleEntity>()!!.property)
    assertEquals("Three", newEntity.children[3].asSafely<MiddleEntity>()!!.property)
  }

  @ParameterizedTest
  @EnumSource(EntityState::class)
  fun `child is added to the end`(enState: EntityState) {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity LeftEntity(MySource) {
      this.children = listOf(
        MiddleEntity("One", MySource),
        MiddleEntity("Two", MySource),
        MiddleEntity("Three", MySource),
      )
    }
    val builder2 = builder.toSnapshot().toBuilder()

    builder2 addEntity MiddleEntity("Four", MySource) {
      this.parentEntity = entity.builderFrom(builder2)
    }

    val newEntity = when (enState) {
      EntityState.FROM_IMMUTABLE -> builder2.toSnapshot().entities<LeftEntity>().single()
      EntityState.FROM_BUILDER -> builder2.entities<LeftEntity>().single()
    }

    assertEquals("One", newEntity.children[0].asSafely<MiddleEntity>()!!.property)
    assertEquals("Two", newEntity.children[1].asSafely<MiddleEntity>()!!.property)
    assertEquals("Three", newEntity.children[2].asSafely<MiddleEntity>()!!.property)
    assertEquals("Four", newEntity.children[3].asSafely<MiddleEntity>()!!.property)
  }

  enum class EntityState {
    FROM_IMMUTABLE,
    FROM_BUILDER,
  }
}