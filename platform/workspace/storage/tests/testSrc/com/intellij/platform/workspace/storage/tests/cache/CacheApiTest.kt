// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.cache

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NameId
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyEntity
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CacheApiTest {
  @Test
  fun testEntities() {
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>()

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element.myName)
  }

  @Test
  fun testEntitiesWithAddingNewEntities() {
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>()

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element.myName)

    val snapshot2 = snapshot.toBuilder().also {
      it addEntity NamedEntity("AnotherEntity", MySource)
    }.toMySnapshot(snapshot)

    val res2 = snapshot2.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2.map { it.myName }, "MyName")
    assertContains(res2.map { it.myName }, "AnotherEntity")
  }

  @Test
  fun testMapping() {
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map { it.myName }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)
  }

  @Test
  fun testMappingWithAdding() {
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map { it.myName }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)

    val snapshot2 = snapshot.toBuilder().also {
      it addEntity NamedEntity("AnotherEntity", MySource)
    }.toMySnapshot(snapshot)

    val res2 = snapshot2.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "MyName")
    assertContains(res2, "AnotherEntity")
  }

  @Test
  fun testMappingWithRemoving() {
    val snapshot = createNamedEntity() {
      this addEntity NamedEntity("AnotherEntity", MySource)
    }
    val query = entities<NamedEntity>().map { it.myName }

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "MyName")
    assertContains(res, "AnotherEntity")

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("AnotherEntity"))!!
      it.removeEntity(entity)
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "MyName")
  }

  @Test
  fun testMappingWithModification() {
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map { it.myName }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyEntity(entity) {
        this.myName = "AnotherName"
      }
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "AnotherName")
  }

  @Test
  fun testMappingWithModificationCheckRecalculation() {
    val snapshot = createNamedEntity() {
      this addEntity NamedEntity("AnotherEntity", MySource)
    }
    var calculationCounter = 0
    val query = entities<NamedEntity>().map {
      calculationCounter += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "MyName")
    assertEquals(2, calculationCounter)

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyEntity(entity) {
        this.myName = "DifferentName"
      }
    }.toMySnapshot(snapshot)

    val res2 = snapshot2.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "DifferentName")
    assertEquals(3, calculationCounter)
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  private fun MutableEntityStorage.toMySnapshot(previous: EntityStorageSnapshot): EntityStorageSnapshot {
    val changes = this.collectChanges(previous)
    return this.instrumentation.toSnapshot(previous, changes)
  }

  private fun createNamedEntity(also: MutableEntityStorage.() -> Unit = {}): EntityStorageSnapshot {
    val builder = createEmptyBuilder()
    builder addEntity NamedEntity("MyName", MySource)
    builder.also()
    return builder.toSnapshot()
  }
}
