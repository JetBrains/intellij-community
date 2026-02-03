// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.ordering

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyNamedEntity
import com.intellij.platform.workspace.storage.tests.builderFrom
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.tests.makeBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Verify ordering of children after the [MutableEntityStorage.applyChangesFrom] operation
 */
class ApplyChangesFromOrderingTest {
  @Test
  fun `keep order of children`() {
    val target = createEmptyBuilder()
    val source = makeBuilder {
      this addEntity NamedEntity("Name", MySource) {
        this.children = listOf(
          NamedChildEntity("One", MySource),
          NamedChildEntity("Two", MySource),
          NamedChildEntity("Three", MySource),
        )
      }
    }

    target.applyChangesFrom(source)

    val snapshot = target.toSnapshot()

    val entity = snapshot.entities<NamedEntity>().single()

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("Three", entity.children[2].childProperty)
  }

  @Test
  fun `only add children`() {
    val target = makeBuilder {
      this addEntity NamedEntity("Name", MySource)
    }
    val source = makeBuilder(target) {
      val parent = this.entities<NamedEntity>().single()
      this.modifyNamedEntity(parent) {
        this.children = listOf(
          NamedChildEntity("One", MySource),
          NamedChildEntity("Two", MySource),
          NamedChildEntity("Three", MySource),
        )
      }
    }

    target.applyChangesFrom(source)

    val snapshot = target.toSnapshot()

    val entity = snapshot.entities<NamedEntity>().single()

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("Three", entity.children[2].childProperty)
  }

  @Test
  fun `add child at the end`() {
    val target = makeBuilder {
      this addEntity NamedEntity("Name", MySource) {
        this.children = listOf(
          NamedChildEntity("One", MySource),
          NamedChildEntity("Two", MySource),
        )
      }
    }
    val source = makeBuilder(target) {
      val parent = this.entities<NamedEntity>().single()
      this.modifyNamedEntity(parent) {
        this.children += NamedChildEntity("Three", MySource)
      }
    }

    target.applyChangesFrom(source)

    val snapshot = target.toSnapshot()

    val entity = snapshot.entities<NamedEntity>().single()

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("Three", entity.children[2].childProperty)
  }

  @Test
  fun `add child at the start`() {
    val target = makeBuilder {
      this addEntity NamedEntity("Name", MySource) {
        this.children = listOf(
          NamedChildEntity("Two", MySource),
          NamedChildEntity("Three", MySource),
        )
      }
    }
    val source = makeBuilder(target) {
      val parent = this.entities<NamedEntity>().single()
      this.modifyNamedEntity(parent) {
        this.children = listOf(NamedChildEntity("One", MySource)) + this.children
      }
    }

    target.applyChangesFrom(source)

    val snapshot = target.toSnapshot()

    val entity = snapshot.entities<NamedEntity>().single()

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("Three", entity.children[2].childProperty)
  }

  @Test
  fun `change order of children`() {
    val target = makeBuilder {
      this addEntity NamedEntity("Name", MySource) {
        this.children = listOf(
          NamedChildEntity("One", MySource),
          NamedChildEntity("Two", MySource),
          NamedChildEntity("Three", MySource),
        )
      }
    }
    val source = makeBuilder(target) {
      val parent = this.entities<NamedEntity>().single()
      this.modifyNamedEntity(parent) {
        this.children = this.children.reversed()
      }
    }

    target.applyChangesFrom(source)

    val snapshot = target.toSnapshot()

    val entity = snapshot.entities<NamedEntity>().single()

    assertEquals("Three", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("One", entity.children[2].childProperty)
  }

  @Test
  fun `move child from middle to the end`() {
    val target = makeBuilder {
      this addEntity NamedEntity("Name", MySource) {
        this.children = listOf(
          NamedChildEntity("One", MySource),
          NamedChildEntity("Two", MySource),
          NamedChildEntity("Three", MySource),
        )
      }
    }
    val source = makeBuilder(target) {
      val parent = this.entities<NamedEntity>().single()
      this.modifyNamedEntity(parent) {
        val toMutableList = this.children.toMutableList()
        val res = toMutableList.removeAt(1)
        toMutableList.add(res)
        this.children = toMutableList
      }
    }

    target.applyChangesFrom(source)

    val snapshot = target.toSnapshot()

    val entity = snapshot.entities<NamedEntity>().single()

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Three", entity.children[1].childProperty)
    assertEquals("Two", entity.children[2].childProperty)
  }

  @Test
  fun `add child at the end by adding a child`() {
    val target = makeBuilder {
      this addEntity NamedEntity("Name", MySource) {
        this.children = listOf(
          NamedChildEntity("One", MySource),
          NamedChildEntity("Two", MySource),
          NamedChildEntity("Three", MySource),
        )
      }
    }
    val source = makeBuilder(target) {
      val parent = this.entities<NamedEntity>().single()
      this addEntity NamedChildEntity("Four", MySource) {
        this.parentEntity = parent.builderFrom(this@makeBuilder)
      }
    }

    val sourceEntity = source.entities<NamedEntity>().single()
    assertEquals("One", sourceEntity.children[0].childProperty)
    assertEquals("Two", sourceEntity.children[1].childProperty)
    assertEquals("Three", sourceEntity.children[2].childProperty)
    assertEquals("Four", sourceEntity.children[3].childProperty)

    target.applyChangesFrom(source)

    val snapshot = target.toSnapshot()

    val entity = snapshot.entities<NamedEntity>().single()

    assertEquals("One", entity.children[0].childProperty)
    assertEquals("Two", entity.children[1].childProperty)
    assertEquals("Three", entity.children[2].childProperty)
    assertEquals("Four", entity.children[3].childProperty)
  }
}
