// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.cache

import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache
import com.intellij.platform.workspace.storage.query.*
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.tests.builderFrom
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.concurrent.thread
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CacheApiTest {
  private val externalMappingKey = ExternalMappingKey.create<Any>("Key")
  @Test
  fun `double access to cache`() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>()
      .map {
        recalculations += 1
        it.myName
      }

    snapshot.cached(query) // First access
    val res = snapshot.cached(query) // Second access
    assertEquals("MyName", res.single())
    assertEquals(1, recalculations)
  }

  @Test
  fun testEntities() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)
    assertEquals(1, recalculations)
  }

  @Test
  fun testMappingWithRemoving() {
    var recalculations = 0
    val snapshot = createNamedEntity {
      this addEntity NamedEntity("AnotherEntity", MySource)
    }
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

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
    assertEquals(2, recalculations)
  }

  @Test
  fun testMappingWithModification() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "AnotherName"
      }
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "AnotherName")
    assertEquals(2, recalculations)

    // Additional call doesn't cause recalculation
    val res3 = snapshot2.cached(query)
    assertEquals(1, res3.size)
    assertContains(res3, "AnotherName")
    assertEquals(2, recalculations)
  }

  @Test
  fun testMappingWithModificationCheckRecalculation() {
    val snapshot = createNamedEntity {
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
      it.modifyNamedEntity(entity) {
        this.myName = "DifferentName"
      }
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "DifferentName")

    assertEquals(3, calculationCounter)
  }

  @Test
  fun testMappingWithModificationManyModifications() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    val builder = snapshot.toBuilder()
    val entity = builder.resolve(NameId("MyName"))!!
    builder.modifyNamedEntity(entity) {
      this.myName = "AnotherName"
    }
    builder.modifyNamedEntity(entity) {
      this.myName = "ThirdName"
    }
    builder.modifyNamedEntity(entity) {
      this.myName = "FourthName"
    }
    val snapshot2 = builder.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "FourthName")
    assertEquals(2, recalculations)

    // Additional call doesn't cause recalculation
    val res3 = snapshot2.cached(query)
    assertEquals(1, res3.size)
    assertContains(res3, "FourthName")
    assertEquals(2, recalculations)
  }


  @Test
  fun testMappingWithModificationFillChangelog() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "AnotherName"
      }
    }.toSnapshot()
    val snapshot3 = snapshot2.toBuilder().also {
      val entity = it.resolve(NameId("AnotherName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "ThirdName"
      }
    }.toSnapshot()
    val snapshot4 = snapshot3.toBuilder().also {
      val entity = it.resolve(NameId("ThirdName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "FourthName"
      }
    }.toSnapshot()

    val res2 = snapshot4.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "FourthName")
    assertEquals(2, recalculations)

    // Additional call doesn't cause recalculation
    val res3 = snapshot4.cached(query)
    assertEquals(1, res3.size)
    assertContains(res3, "FourthName")
    assertEquals(2, recalculations)
  }

  @Test
  fun testMappingWithModificationFillChangelogAndDeleteEntity() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "AnotherName"
      }
    }.toSnapshot()
    val snapshot3 = snapshot2.toBuilder().also {
      val entity = it.resolve(NameId("AnotherName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "ThirdName"
      }
    }.toSnapshot()
    val snapshot4 = snapshot3.toBuilder().also {
      val entity = it.resolve(NameId("ThirdName"))!!
      it.removeEntity(entity)
    }.toSnapshot()

    val res2 = snapshot4.cached(query)
    assertEquals(0, res2.size)
    assertEquals(1, recalculations)
  }

  @Test
  fun testMapAndGroupBy() {
    val snapshot = createNamedEntity {
      val parent = this.resolve(NameId("MyName"))!!
      this addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
      this addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
    }
    val query = entities<NamedEntity>()
      .flatMap { namedEntity, _ -> namedEntity.children }
      .groupBy({ it.childProperty }, { it.entitySource })

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertEquals(MySource, res["prop1"]!!.single())
    assertEquals(MySource, res["prop2"]!!.single())

    val snapshot2 = snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(parent) {
        this.myName = "AnotherName"
      }
      it addEntity NamedChildEntity("prop3", AnotherSource) {
        this.parentEntity = parent.builderFrom(it)
      }
    }

    val res2 = snapshot2.cached(query)
    assertEquals(3, res2.size)
    assertEquals(MySource, res2["prop1"]!!.single())
    assertEquals(MySource, res2["prop2"]!!.single())
    assertEquals(AnotherSource, res2["prop3"]!!.single())
  }

  @Test
  fun testGroupBy() {
    val snapshot = createNamedEntity {
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
  @Disabled("entitiesByExternalMapping is not supported")
  fun testMapToChildAndAddNewChild() {
    var flatMapRecalc = 0
    var mapRecalc = 0
    val snapshot = createNamedEntity {
      val parent = this.resolve(NameId("MyName"))!!
      this.getMutableExternalMapping(externalMappingKey).addMapping(parent, "externalInfo")
      this addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
      this addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
    }
    val query = entitiesByExternalMapping("test", "externalInfo").flatMap { entity, _ ->
      flatMapRecalc += 1
      (entity as NamedEntity).children
    }.map {
      mapRecalc += 1
      it.childProperty
    }

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "prop1")
    assertContains(res, "prop2")
    assertEquals(1, flatMapRecalc)
    assertEquals(2, mapRecalc)

    val snapshot2 = snapshot.toBuilder().also {
      val parent = it.resolve(NameId("MyName"))!!
      it addEntity NamedChildEntity("prop3", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(3, res2.size)
    assertContains(res2, "prop1")
    assertContains(res2, "prop2")
    assertContains(res2, "prop3")
    assertEquals(2, flatMapRecalc)
    assertEquals(5, mapRecalc)
  }

  @Test
  fun testModifyUnrelatedField() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.entitySource = AnotherSource
      }
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "MyName")
    assertEquals(2, recalculations)
  }

  @Test
  fun testMappingToChildrenRemoveChild() {
    var flatMapRecalc = 0
    var mapRecalc = 0
    val snapshot = createNamedEntity {
      val parent = this.resolve(NameId("MyName"))!!
      this addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
      this addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
    }
    val query = entities<NamedEntity>().flatMap { entity, _ ->
      flatMapRecalc += 1
      entity.children
    }.map {
      mapRecalc += 1
      it.childProperty
    }

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "prop1")
    assertContains(res, "prop2")
    assertEquals(1, flatMapRecalc)
    assertEquals(2, mapRecalc)

    val newSnapshot = snapshot.toBuilder().also { mutableStorage ->
      val child = mutableStorage.entities(NamedChildEntity::class.java).single { it.childProperty == "prop1" }
      mutableStorage.removeEntity(child)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "prop2")
    assertEquals(2, flatMapRecalc)
    assertEquals(3, mapRecalc)
  }

  @Test
  fun testModifyField() {
    var recalculations = 0
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)

    val snapshot2 = snapshot.toBuilder().also {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "NewName"
      }
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "NewName")
    assertEquals(2, recalculations)
  }


  @Test
  fun testMapToChildAndModifyChild() {
    var flatMapRecalc = 0
    var mapRecalc = 0
    val snapshot = createNamedEntity {
      val parent = this.resolve(NameId("MyName"))!!
      this addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
      this addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
    }
    val query = entities<NamedEntity>().flatMap { entity, _ ->
      flatMapRecalc += 1
      entity.children
    }.map {
      mapRecalc += 1
      it.childProperty
    }

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "prop1")
    assertContains(res, "prop2")
    assertEquals(1, flatMapRecalc)
    assertEquals(2, mapRecalc)

    val snapshot2 = snapshot.toBuilder().also {
      val parent = it.resolve(NameId("MyName"))!!
      val child = parent.children.first()
      it.modifyNamedChildEntity(child) {
        this.childProperty = "AnotherProp"
      }
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "AnotherProp")
    assertContains(res2, "prop2")
    assertEquals(1, flatMapRecalc)
    assertEquals(3, mapRecalc)
  }

  @Test
  fun testMapWithSameValues() {
    var mapRecalc = 0
    var mapTwoRecalc = 0
    var mapThreeRecalc = 0
    val snapshot = createNamedEntity {
      val parent = this.resolve(NameId("MyName"))!!
      this addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
      this addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
    }
    val query = entities<NamedChildEntity>().map {
      mapRecalc += 1
      it.childProperty
    }.map {
      mapTwoRecalc += 1
      it + "XYZ"
    }.map {
      mapThreeRecalc += 1
      it + "ABC"
    }

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertEquals(1, res.toSet().size)
    assertContains(res, "prop1XYZABC")
    assertEquals(2, mapRecalc)
    assertEquals(2, mapTwoRecalc)
    assertEquals(2, mapThreeRecalc)

    val snapshot2 = snapshot.toBuilder().also {
      val parent = it.resolve(NameId("MyName"))!!
      val child = parent.children.first()
      it.removeEntity(child)
    }.toSnapshot()

    val res2 = snapshot2.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "prop1XYZABC")
    assertEquals(2, mapRecalc)
    assertEquals(2, mapTwoRecalc)
    assertEquals(2, mapThreeRecalc)
  }

  @Test
  fun testGroupByWithAddingNewValue() {
    var calculationCounter = 0
    val snapshot = createNamedEntity {
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
    val snapshot = createNamedEntity {
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
    val snapshot = createNamedEntity {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, {
      calculationCounter += 1
      it.entitySource
    })

    snapshot.cached(query)

    val newSnapshot = snapshot.toBuilder().also {
      it.modifyNamedEntity(it.resolve(NameId("AnotherEntity"))!!) {
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
    val snapshot = createNamedEntity {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.entitySource }, {
      calculationCounter += 1
      it.myName
    })

    snapshot.cached(query)

    val newSnapshot = snapshot.toBuilder().also {
      it.modifyNamedEntity(it.resolve(NameId("AnotherEntity"))!!) {
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

  @Test
  fun testMapToExternalMapping() {
    val snapshot = createNamedEntity {
      val entity = this addEntity NamedEntity("AnotherEntity", AnotherSource)
      this.getMutableExternalMapping(externalMappingKey).addMapping(entity, 1)
    }
    val query = entities<NamedEntity>().mapWithSnapshot { entity, mySnapshot ->
      mySnapshot.getExternalMapping(externalMappingKey).getDataByEntity(entity)
    }

    val res = snapshot.cached(query)

    assertEquals(setOf(null, 1), res.toSet())

    val newSnapshot = snapshot.toBuilder().also {
      it.getMutableExternalMapping(externalMappingKey).addMapping(it.resolve(NameId("MyName"))!!, 2)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(setOf(1, 2), res2.toSet())
  }

  @Test
  @Disabled("entitiesByExternalMapping is not supported")
  fun testMapToExternalMappingExtract() {
    val snapshot = createNamedEntity {
      val entity = this addEntity NamedEntity("AnotherEntity", AnotherSource)
      this.getMutableExternalMapping(externalMappingKey).addMapping(entity, 1)
    }
    val query = entitiesByExternalMapping("test", 1).map { (it as NamedEntity).myName }

    val res = snapshot.cached(query)

    assertEquals("AnotherEntity", res.single())

    val newSnapshot = snapshot.toBuilder().also {
      it.getMutableExternalMapping(externalMappingKey).removeMapping(it.resolve(NameId("AnotherEntity"))!!)
      it.getMutableExternalMapping(externalMappingKey).addMapping(it.resolve(NameId("MyName"))!!, 1)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals("MyName", res2.single())
  }

  @Test
  @Disabled("entitiesByExternalMapping is not supported")
  fun testReplaceMapping() {
    val snapshot = createNamedEntity {
      val entity = this addEntity NamedEntity("AnotherEntity", AnotherSource)
      this.getMutableExternalMapping(externalMappingKey).addMapping(entity, 1)
    }
    val query = entitiesByExternalMapping("test", 1).map { (it as NamedEntity).myName }

    val res = snapshot.cached(query)

    assertEquals("AnotherEntity", res.single())

    val newSnapshot = snapshot.toBuilder().also {
      it.getMutableExternalMapping(externalMappingKey).addMapping(it.resolve(NameId("AnotherEntity"))!!, 2)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertTrue(res2.isEmpty())
  }

  @Test
  fun filter() {
    var calculationCounter = 0
    val snapshot = createNamedEntity {
      this addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().filter {
      calculationCounter += 1
      it.myName.startsWith("My")
    }
      .map { it.myName }

    val res = snapshot.cached(query)

    assertEquals("MyName", res.single())

    val newSnapshot = snapshot.toBuilder().also {
      it addEntity NamedEntity("MySuperEntity", MySource)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertContains(res2, "MyName")
    assertContains(res2, "MySuperEntity")
    assertEquals(3, calculationCounter)
  }

  @Test
  fun equalMappings() {
    val snapshot = createNamedEntity {
      this addEntity NamedEntity("AnotherEntity", MySource)
    }
    val query = entities<NamedEntity>().map {
      it.entitySource.toString()
    }
      .map { it + 1 }

    val res = snapshot.cached(query)

    assertEquals(2, res.size)

    val newSnapshot = snapshot.toBuilder().also {
      it.removeEntity(it.resolve(NameId("AnotherEntity"))!!)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(1, res2.size)
  }

  @Test
  fun equalGroupBy() {
    val snapshot = createNamedEntity {
      this addEntity NamedEntity("AnotherEntity", MySource)
    }
    val query = entities<NamedEntity>().map {
      it.entitySource.toString()
    }
      .groupBy({ it.take(1) }, { it.takeLast(1) })

    val res = snapshot.cached(query)

    assertEquals(1, res.size)

    val newSnapshot = snapshot.toBuilder().also {
      it.removeEntity(it.resolve(NameId("AnotherEntity"))!!)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(1, res2.size)
  }

  @Test
  @Disabled("entitiesByExternalMapping is not supported")
  fun addAndRemoveEntity() {
    val snapshot = createNamedEntity {
      val entity = this.entities(NamedEntity::class.java).single()
      this.getMutableExternalMapping(externalMappingKey).addMapping(entity, "data")
    }
    val query = entitiesByExternalMapping("test", "data").map {
      it.entitySource
    }

    val res = snapshot.cached(query)

    assertEquals(1, res.size)

    val newSnapshot = snapshot.toBuilder().also {
      val entity = it addEntity NamedEntity("EntityOne", MySource)
      it.removeEntity(entity)
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals(1, res2.size)
  }

  @Test
  fun `modify field`() {
    var calculationCounter = 0
    val snapshot = createNamedEntity {
      val parent = this.entities(NamedEntity::class.java).single()
      this addEntity NamedChildEntity("Child", MySource) {
        this.parentEntity = parent.builderFrom(this@createNamedEntity)
      }
    }
    val query = entities<NamedEntity>()
      .flatMap { namedEntity, _ ->
        namedEntity.children
      }
      .map {
        calculationCounter += 1
        it.childProperty
      }

    val res = snapshot.cached(query)

    assertEquals("Child", res.single())

    val newSnapshot = snapshot.toBuilder().also {
      val child = it.entities(NamedChildEntity::class.java).single()
      it.modifyNamedChildEntity(child) {
        this.childProperty = "AnotherValue"
      }
    }.toSnapshot()

    val res2 = newSnapshot.cached(query)
    assertEquals("AnotherValue", res2.single())
    assertEquals(2, calculationCounter)
  }

  @Test
  @Disabled("entitiesByExternalMapping is not supported")
  fun `request with updates in unrelated entity`() {
    val builder = MutableEntityStorage.create()

    // Set initial state
    val parent = builder addEntity ParentEntity("data", MySource)

    val data = "ExternalInfo"
    builder.getMutableExternalMapping(externalMappingKey).addMapping(parent, data)
    builder addEntity ParentMultipleEntity("data", MySource) {
      this.children = List(10) {
        ChildMultipleEntity("data$it", MySource)
      }
    }
    val snapshot = builder.toSnapshot()

    val modifiedEntitySource = entitiesByExternalMapping("Test", "ExternalInfo")
      .map { it.entitySource }
      .map { it.toString() + "X" }

    snapshot.cached(modifiedEntitySource)

    val newBuilder = snapshot.toBuilder()
    val mutableMapping = newBuilder.getMutableExternalMapping(externalMappingKey)
    mutableMapping.getEntities("ExternalInfo").forEach {
      mutableMapping.addMapping(it, "AnotherMapping")
    }

    newBuilder.entities(ChildMultipleEntity::class.java).filter { it.childData.removePrefix("data").toInt() % 2 == 0 }.forEach {
      newBuilder.removeEntity(it)
    }
    val newSnapshot = newBuilder.toSnapshot()
    val res = newSnapshot.cached(modifiedEntitySource)
    assertTrue(res.isEmpty())
  }

  @Test
  fun `request with double update`() {
    val builder = MutableEntityStorage.create()

    // Set initial state
    builder addEntity NamedEntity("MyName", MySource)
    val snapshot = builder.toSnapshot()

    val namesOfNamedEntities = entities<NamedEntity>().map { it.myName }

    snapshot.cached(namesOfNamedEntities)

    // Modify snapshot
    val newBuilder = snapshot.toBuilder()
    newBuilder addEntity NamedEntity("MyNameXYZ", MySource)

    val newSnapshot = newBuilder.toSnapshot()

    // Do request after modification
    newSnapshot.cached(namesOfNamedEntities)

    println("Modify snapshots second time")
    val thirdBuilder = newSnapshot.toBuilder()

    // Remove some random entities
    val value = thirdBuilder.entities(NamedEntity::class.java).first()
    thirdBuilder.removeEntity(value)
    val snapshotAfterRemoval = thirdBuilder.toSnapshot()

    println("Read third time")
    val res = snapshotAfterRemoval.cached(namesOfNamedEntities)

    assertContains(res, "MyNameXYZ")
  }

  @Test
  fun `add many changes but cache is NOT reset`() {
    var recalculations = 0
    var snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)
    assertEquals(1, recalculations)

    repeat(TracedSnapshotCache.LOG_QUEUE_MAX_SIZE - 2) { counter ->
      snapshot = snapshot.update {
        if (counter % 2 == 0) {
          it addEntity NamedEntity("X", MySource)
        }
        else {
          it.removeEntity(it.resolve(NameId("X"))!!)
        }
      }
    }

    val res2 = snapshot.cached(query)
    val element2 = res2.single()
    assertEquals("MyName", element2)
    assertEquals(1, recalculations)
  }

  @Test
  fun `add many changes and cache is reset`() {
    var recalculations = 0
    var snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)
    assertEquals(1, recalculations)

    repeat(TracedSnapshotCache.LOG_QUEUE_MAX_SIZE + 2) { counter ->
      snapshot = snapshot.update {
        if (counter % 2 == 0) {
          it addEntity NamedEntity("X", MySource)
        }
        else {
          it.removeEntity(it.resolve(NameId("X"))!!)
        }
      }
    }

    val res2 = snapshot.cached(query)
    val element2 = res2.single()
    assertEquals("MyName", element2)
    assertEquals(2, recalculations)
  }


  @Test
  fun testTwoBranchesFromSameBuilder() {
    val snapshot = createNamedEntity()
    val query = entities<NamedEntity>().map {
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)

    val builder = snapshot.update {
      it addEntity NamedEntity("AnotherName", MySource)
    }.toBuilder().also {
      it.entities(NamedEntity::class.java).forEach { entity -> it.removeEntity(entity) }
    }

    builder.toSnapshot() // First snapshot
    val snapshotTwo = builder.toSnapshot() // Second snapshot

    val res2 = snapshotTwo.cached(query)
    assertTrue(res2.isEmpty())
  }

  @Test
  fun `concurrency test create new snapshot with parallel read`() {
    val builder = MutableEntityStorage.create()

    builder addEntity SampleEntity2("info", false, MySource)

    val snapshot = builder.toSnapshot()

    val query = entities<SampleEntity2>().map { it.data }
    val res = snapshot.cached(query)
    assertEquals("info", res.single())

    repeat(10_000) {
      val builder2 = snapshot.toBuilder()
      repeat(1000) {
        builder2 addEntity SampleEntity2("info$it", false, MySource)
      }

      val snapshot2 = builder2.toSnapshot()

      var exceptionOne: Throwable? = null
      var exceptionTwo: Throwable? = null
      val threadOne = thread {
        exceptionOne = runCatching {
          val res2 = snapshot2.cached(query)
          assertEquals(1001, res2.size)
        }.exceptionOrNull()
      }
      val threadTwo = thread {
        exceptionTwo = runCatching {
          val snapshot3 = snapshot2.toBuilder().toSnapshot()
          assertEquals(1001, snapshot3.cached(query).size)
        }.exceptionOrNull()
      }

      threadOne.join()
      threadTwo.join()
      exceptionOne?.let { fail("Exception in first thread", it) }
      exceptionTwo?.let { fail("Exception in second thread", it) }
    }
  }

  @Test
  fun `concurrency smoke test parallel read`() {
    val builder = MutableEntityStorage.create()

    repeat(1000) {
      builder addEntity SampleEntity2("info$it", false, MySource)
    }

    val snapshot = builder.toSnapshot()

    val query = entities<SampleEntity2>().map { it.data }

    val results = arrayOfNulls<Collection<String>>(1000)
    val threads = List(1000) {
      thread { results[it] = snapshot.cached(query) }
    }

    threads.forEach { it.join() }

    assertAll(
      results.map { { assertEquals(1000, it?.size ?: 0) } }
    )
  }

  @Test
  fun `concurrency smoke test parallel read with existing modificaitons`() {
    val query = entities<SampleEntity2>().map { it.data }

    val initialSnapshot = MutableEntityStorage.create().toSnapshot()

    assertTrue(initialSnapshot.cached(query).isEmpty())

    val builder = initialSnapshot.toBuilder()

    repeat(1000) {
      builder addEntity SampleEntity2("info$it", false, MySource)
    }

    val snapshot = builder.toSnapshot()

    val results = arrayOfNulls<Collection<String>>(1000)
    val threads = List(1000) {
      thread { results[it] = snapshot.cached(query) }
    }

    threads.forEach { it.join() }

    assertAll(
      results.map { { assertEquals(1000, it?.size ?: 0) } }
    )
  }

  @Test
  fun `request of cache with removing an entity`() {
    val builder = MutableEntityStorage.create()
    builder addEntity ParentMultipleEntity("data1", MySource) {
      this.children = listOf(ChildMultipleEntity("data1", MySource))
    }
    builder addEntity ParentMultipleEntity("data2", MySource) {
      this.children = listOf(ChildMultipleEntity("data2", MySource))
    }
    val snapshot = builder.toSnapshot()

    val childData = entities<ParentMultipleEntity>()
      .flatMap { parentEntity, _ -> parentEntity.children }
      .map { it.childData }

    snapshot.cached(childData)

    val builder1 = snapshot.toBuilder()
    builder1.entities(ChildMultipleEntity::class.java).forEach {
      builder1.removeEntity(it)
    }
    val newSnapshot = builder1.toSnapshot()

    newSnapshot.cached(childData)
  }

  private fun createNamedEntity(also: MutableEntityStorage.() -> Unit = {}): ImmutableEntityStorage {
    val builder = createEmptyBuilder()
    builder addEntity NamedEntity("MyName", MySource)
    builder.also()
    return builder.toSnapshot()
  }

  private fun ImmutableEntityStorage.update(fc: (MutableEntityStorage) -> Unit): ImmutableEntityStorage {
    return this.toBuilder().also(fc).toSnapshot()
  }
}