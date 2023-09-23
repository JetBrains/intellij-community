// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.cache

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.groupBy
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.junit5.TestApplication
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
    }.toSnapshot()

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
    }.toSnapshot()

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
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "DifferentName")
    assertEquals(3, calculationCounter)
  }

  @Test
  fun testGroupBy() {
    val snapshot = createNamedEntity() {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, { it.entitySource })

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "MyName")
    assertEquals(MySource, res["MyName"]!!.single())
    assertContains(res, "AnotherEntity")
    assertEquals(AnotherSource, res["AnotherEntity"]!!.single())
  }

  @Test
  fun testGroupByWithAddingNewValue() {
    var calculationCounter = 0
    val snapshot = createNamedEntity() {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, {
      calculationCounter += 1
      it.entitySource
    })

    snapshot.cached(query)

    val newSnapshot = snapshot.toBuilder().also {
      it addEntity NamedEntity("ThirdEntity", MySource)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(3, res2.size)
    assertContains(res2, "MyName")
    assertEquals(MySource, res2["MyName"]!!.single())
    assertContains(res2, "AnotherEntity")
    assertEquals(AnotherSource, res2["AnotherEntity"]!!.single())
    assertContains(res2, "ThirdEntity")
    assertEquals(MySource, res2["ThirdEntity"]!!.single())
    assertEquals(3, calculationCounter)
  }

  @Test
  fun testGroupByWithRemovingValue() {
    var calculationCounter = 0
    val snapshot = createNamedEntity() {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, {
      calculationCounter += 1
      it.entitySource
    })

    snapshot.cached(query)

    val newSnapshot = snapshot.toBuilder().also {
      it.removeEntity(it.resolve(NameId("AnotherEntity"))!!)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "MyName")
    assertEquals(MySource, res2["MyName"]!!.single())
  }

  @Test
  fun testGroupByWithModifyingValue() {
    var calculationCounter = 0
    val snapshot = createNamedEntity() {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, {
      calculationCounter += 1
      it.entitySource
    })

    snapshot.cached(query)

    val newSnapshot = snapshot.toBuilder().also {
      it.modifyEntity(it.resolve(NameId("AnotherEntity"))!!) {
        this.entitySource = MySource
      }
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "MyName")
    assertEquals(MySource, res2["MyName"]!!.single())
    assertContains(res2, "AnotherEntity")
    assertEquals(MySource, res2["AnotherEntity"]!!.single())
  }

  @Test
  fun testGroupByJoinValues() {
    var calculationCounter = 0
    val snapshot = createNamedEntity() {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.entitySource }, {
      calculationCounter += 1
      it.myName
    })

    snapshot.cached(query)

    val newSnapshot = snapshot.toBuilder().also {
      it.modifyEntity(it.resolve(NameId("AnotherEntity"))!!) {
        this.entitySource = MySource
      }
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, MySource)
    val values = res2[MySource]!!
    assertContains(values, "AnotherEntity")
    assertContains(values, "MyName")
  }

  private fun createNamedEntity(also: MutableEntityStorage.() -> Unit = {}): EntityStorageSnapshot {
    val builder = createEmptyBuilder()
    builder addEntity NamedEntity("MyName", MySource)
    builder.also()
    return builder.toSnapshot()
  }
}
