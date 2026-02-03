// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.cache

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCacheImpl
import com.intellij.platform.workspace.storage.query.*
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.tests.builderFrom
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.testFramework.LeakHunter
import org.junit.jupiter.api.*
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheApiTest {
  private val externalMappingKey = ExternalMappingKey.create<Any>("Key")
  private lateinit var snapshot: ImmutableEntityStorage

  @BeforeEach
  fun setUp(info: RepetitionInfo) {
    snapshot = createNamedEntity()
    // Random returns same result for nextInt(2) for the first 4095 seeds, so we generated random seed
    ((snapshot as ImmutableEntityStorageImpl).snapshotCache as TracedSnapshotCacheImpl).shuffleEntities = Random(
      info.currentRepetition.toLong()
    ).nextLong()
  }

  @AfterEach
  fun tearDown() {
    val snapshotCache = (snapshot as ImmutableEntityStorageImpl).snapshotCache
    LeakHunter.checkLeak(snapshotCache, EntityStorage::class.java)
  }

  @RepeatedTest(10)
  fun `double access to cache`() {
    var recalculations = 0
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

  @RepeatedTest(10)
  fun testEntities() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)
    assertEquals(1, recalculations)
  }

  @RepeatedTest(10)
  fun testMappingWithRemoving() {
    var recalculations = 0
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", MySource)
    }
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "MyName")
    assertContains(res, "AnotherEntity")

    snapshot.update {
      val entity = it.resolve(NameId("AnotherEntity"))!!
      it.removeEntity(entity)
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "MyName")
    assertEquals(2, recalculations)
  }

  @RepeatedTest(10)
  fun testMappingWithModification() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    snapshot.update {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "AnotherName"
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "AnotherName")
    assertEquals(2, recalculations)

    // Additional call doesn't cause recalculation
    val res3 = snapshot.cached(query)
    assertEquals(1, res3.size)
    assertContains(res3, "AnotherName")
    assertEquals(2, recalculations)
  }

  @RepeatedTest(10)
  fun testMappingWithModificationCheckRecalculation() {
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", MySource)
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

    snapshot.update {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "DifferentName"
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "DifferentName")

    assertEquals(3, calculationCounter)
  }

  @RepeatedTest(10)
  fun testMappingWithModificationManyModifications() {
    var recalculations = 0
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
    snapshot = builder.toSnapshot()

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "FourthName")
    assertEquals(2, recalculations)

    // Additional call doesn't cause recalculation
    val res3 = snapshot.cached(query)
    assertEquals(1, res3.size)
    assertContains(res3, "FourthName")
    assertEquals(2, recalculations)
  }


  @RepeatedTest(10)
  fun testMappingWithModificationFillChangelog() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    snapshot.update {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "AnotherName"
      }
    }
    snapshot.update {
      val entity = it.resolve(NameId("AnotherName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "ThirdName"
      }
    }
    snapshot.update {
      val entity = it.resolve(NameId("ThirdName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "FourthName"
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "FourthName")
    assertEquals(2, recalculations)

    // Additional call doesn't cause recalculation
    val res3 = snapshot.cached(query)
    assertEquals(1, res3.size)
    assertContains(res3, "FourthName")
    assertEquals(2, recalculations)
  }

  @RepeatedTest(10)
  fun testMappingWithModificationFillChangelogAndDeleteEntity() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    assertEquals(1, res.size)
    assertContains(res, "MyName")

    snapshot.update {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "AnotherName"
      }
    }
    snapshot.update {
      val entity = it.resolve(NameId("AnotherName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "ThirdName"
      }
    }
    snapshot.update {
      val entity = it.resolve(NameId("ThirdName"))!!
      it.removeEntity(entity)
    }

    val res2 = snapshot.cached(query)
    assertEquals(0, res2.size)
    assertEquals(1, recalculations)
  }

  @RepeatedTest(10)
  fun testMapAndGroupBy() {
    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
      it addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
    }
    val query = entities<NamedEntity>()
      .flatMap { namedEntity, _ -> namedEntity.children }
      .groupBy({ it.childProperty }, { it.entitySource })

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertEquals(MySource, res["prop1"]!!.single())
    assertEquals(MySource, res["prop2"]!!.single())

    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(parent) {
        this.myName = "AnotherName"
      }
      it addEntity NamedChildEntity("prop3", AnotherSource) {
        this.parentEntity = parent.builderFrom(it)
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(3, res2.size)
    assertEquals(MySource, res2["prop1"]!!.single())
    assertEquals(MySource, res2["prop2"]!!.single())
    assertEquals(AnotherSource, res2["prop3"]!!.single())
  }

  @RepeatedTest(10)
  fun testGroupBy() {
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, { it.entitySource })

    val res = snapshot.cached(query)
    assertEquals(2, res.size)
    assertContains(res, "MyName")
    assertEquals(MySource, res["MyName"]!!.single())
    assertContains(res, "AnotherEntity")
    assertEquals(AnotherSource, res["AnotherEntity"]!!.single())
  }

  @RepeatedTest(10)
  @Disabled("entitiesByExternalMapping is not supported")
  fun testMapToChildAndAddNewChild() {
    var flatMapRecalc = 0
    var mapRecalc = 0
    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it.getMutableExternalMapping(externalMappingKey).addMapping(parent, "externalInfo")
      it addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
      it addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(it)
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

    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it addEntity NamedChildEntity("prop3", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(3, res2.size)
    assertContains(res2, "prop1")
    assertContains(res2, "prop2")
    assertContains(res2, "prop3")
    assertEquals(2, flatMapRecalc)
    assertEquals(5, mapRecalc)
  }

  @RepeatedTest(10)
  fun testModifyUnrelatedField() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)

    snapshot.update {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.entitySource = AnotherSource
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "MyName")
    assertEquals(2, recalculations)
  }

  @RepeatedTest(10)
  fun testMappingToChildrenRemoveChild() {
    var flatMapRecalc = 0
    var mapRecalc = 0
    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
      it addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(it)
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

    snapshot.update { mutableStorage ->
      val child = mutableStorage.entities(NamedChildEntity::class.java).single { it.childProperty == "prop1" }
      mutableStorage.removeEntity(child)
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "prop2")
    assertEquals(2, flatMapRecalc)
    assertEquals(3, mapRecalc)
  }

  @RepeatedTest(10)
  fun testModifyField() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)

    snapshot.update {
      val entity = it.resolve(NameId("MyName"))!!
      it.modifyNamedEntity(entity) {
        this.myName = "NewName"
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "NewName")
    assertEquals(2, recalculations)
  }


  @RepeatedTest(10)
  fun testMapToChildAndModifyChild() {
    var flatMapRecalc = 0
    var mapRecalc = 0
    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
      it addEntity NamedChildEntity("prop2", MySource) {
        this.parentEntity = parent.builderFrom(it)
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

    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      val child = parent.children.first()
      it.modifyNamedChildEntity(child) {
        this.childProperty = "AnotherProp"
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "AnotherProp")
    assertContains(res2, "prop2")
    assertEquals(1, flatMapRecalc)
    assertEquals(3, mapRecalc)
  }

  @RepeatedTest(10)
  fun testMapWithSameValues() {
    var mapRecalc = 0
    var mapTwoRecalc = 0
    var mapThreeRecalc = 0
    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      it addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(it)
      }
      it addEntity NamedChildEntity("prop1", MySource) {
        this.parentEntity = parent.builderFrom(it)
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

    snapshot.update {
      val parent = it.resolve(NameId("MyName"))!!
      val child = parent.children.first()
      it.removeEntity(child)
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "prop1XYZABC")
    assertEquals(2, mapRecalc)
    assertEquals(2, mapTwoRecalc)
    assertEquals(2, mapThreeRecalc)
  }

  @RepeatedTest(10)
  fun testGroupByWithAddingNewValue() {
    var calculationCounter = 0
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, {
      calculationCounter += 1
      it.entitySource
    })

    snapshot.cached(query)

    snapshot.update {
      it addEntity NamedEntity("ThirdEntity", MySource)
    }

    val res2 = snapshot.cached(query)
    assertEquals(3, res2.size)
    assertContains(res2, "MyName")
    assertEquals(MySource, res2["MyName"]!!.single())
    assertContains(res2, "AnotherEntity")
    assertEquals(AnotherSource, res2["AnotherEntity"]!!.single())
    assertContains(res2, "ThirdEntity")
    assertEquals(MySource, res2["ThirdEntity"]!!.single())
    assertEquals(3, calculationCounter)
  }

  @RepeatedTest(10)
  fun testGroupByWithRemovingValue() {
    var calculationCounter = 0
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, {
      calculationCounter += 1
      it.entitySource
    })

    snapshot.cached(query)

    snapshot.update {
      it.removeEntity(it.resolve(NameId("AnotherEntity"))!!)
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, "MyName")
    assertEquals(MySource, res2["MyName"]!!.single())
  }

  @RepeatedTest(10)
  fun testGroupByWithModifyingValue() {
    var calculationCounter = 0
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.myName }, {
      calculationCounter += 1
      it.entitySource
    })

    snapshot.cached(query)

    snapshot.update {
      it.modifyNamedEntity(it.resolve(NameId("AnotherEntity"))!!) {
        this.entitySource = MySource
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(2, res2.size)
    assertContains(res2, "MyName")
    assertEquals(MySource, res2["MyName"]!!.single())
    assertContains(res2, "AnotherEntity")
    assertEquals(MySource, res2["AnotherEntity"]!!.single())
  }

  @RepeatedTest(10)
  fun testGroupByJoinValues() {
    var calculationCounter = 0
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().groupBy({ it.entitySource }, {
      calculationCounter += 1
      it.myName
    })

    snapshot.cached(query)

    snapshot.update {
      it.modifyNamedEntity(it.resolve(NameId("AnotherEntity"))!!) {
        this.entitySource = MySource
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
    assertContains(res2, MySource)
    val values = res2[MySource]!!
    assertContains(values, "AnotherEntity")
    assertContains(values, "MyName")
  }

  @RepeatedTest(10)
  fun testMapToExternalMapping() {
    // Without it, the map function captures the whole class and the snapshot with it what causes leakage check to fail
    val key = externalMappingKey

    snapshot.update {
      val entity = it addEntity NamedEntity("AnotherEntity", AnotherSource)
      it.getMutableExternalMapping(key).addMapping(entity, 1)
    }
    val query = entities<NamedEntity>().mapWithSnapshot { entity, mySnapshot ->
      mySnapshot.getExternalMapping(key).getDataByEntity(entity)
    }

    val res = snapshot.cached(query)

    assertEquals(setOf(null, 1), res.toSet())

    snapshot.update {
      it.getMutableExternalMapping(key).addMapping(it.resolve(NameId("MyName"))!!, 2)
    }

    val res2 = snapshot.cached(query)
    assertEquals(setOf(1, 2), res2.toSet())
  }

  @RepeatedTest(10)
  @Disabled("entitiesByExternalMapping is not supported")
  fun testMapToExternalMappingExtract() {
    snapshot.update {
      val entity = it addEntity NamedEntity("AnotherEntity", AnotherSource)
      it.getMutableExternalMapping(externalMappingKey).addMapping(entity, 1)
    }
    val query = entitiesByExternalMapping("test", 1).map { (it as NamedEntity).myName }

    val res = snapshot.cached(query)

    assertEquals("AnotherEntity", res.single())

    snapshot.update {
      it.getMutableExternalMapping(externalMappingKey).removeMapping(it.resolve(NameId("AnotherEntity"))!!)
      it.getMutableExternalMapping(externalMappingKey).addMapping(it.resolve(NameId("MyName"))!!, 1)
    }

    val res2 = snapshot.cached(query)
    assertEquals("MyName", res2.single())
  }

  @RepeatedTest(10)
  @Disabled("entitiesByExternalMapping is not supported")
  fun testReplaceMapping() {
    snapshot.update {
      val entity = it addEntity NamedEntity("AnotherEntity", AnotherSource)
      it.getMutableExternalMapping(externalMappingKey).addMapping(entity, 1)
    }
    val query = entitiesByExternalMapping("test", 1).map { (it as NamedEntity).myName }

    val res = snapshot.cached(query)

    assertEquals("AnotherEntity", res.single())

    snapshot.update {
      it.getMutableExternalMapping(externalMappingKey).addMapping(it.resolve(NameId("AnotherEntity"))!!, 2)
    }

    val res2 = snapshot.cached(query)
    assertTrue(res2.isEmpty())
  }

  @RepeatedTest(10)
  fun filter() {
    var calculationCounter = 0
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", AnotherSource)
    }
    val query = entities<NamedEntity>().filter {
      calculationCounter += 1
      it.myName.startsWith("My")
    }
      .map { it.myName }

    val res = snapshot.cached(query)

    assertEquals("MyName", res.single())

    snapshot.update {
      it addEntity NamedEntity("MySuperEntity", MySource)
    }

    val res2 = snapshot.cached(query)
    assertContains(res2, "MyName")
    assertContains(res2, "MySuperEntity")
    assertEquals(3, calculationCounter)
  }

  @RepeatedTest(10)
  fun equalMappings() {
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", MySource)
    }
    val query = entities<NamedEntity>().map {
      it.entitySource.toString()
    }
      .map { it + 1 }

    val res = snapshot.cached(query)

    assertEquals(2, res.size)

    snapshot.update {
      it.removeEntity(it.resolve(NameId("AnotherEntity"))!!)
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
  }

  @RepeatedTest(10)
  fun equalGroupBy() {
    snapshot.update {
      it addEntity NamedEntity("AnotherEntity", MySource)
    }
    val query = entities<NamedEntity>().map {
      it.entitySource.toString()
    }
      .groupBy({ it.take(1) }, { it.takeLast(1) })

    val res = snapshot.cached(query)

    assertEquals(1, res.size)

    snapshot.update {
      it.removeEntity(it.resolve(NameId("AnotherEntity"))!!)
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
  }

  @RepeatedTest(10)
  @Disabled("entitiesByExternalMapping is not supported")
  fun addAndRemoveEntity() {
    snapshot.update {
      val entity = it.entities(NamedEntity::class.java).single()
      it.getMutableExternalMapping(externalMappingKey).addMapping(entity, "data")
    }
    val query = entitiesByExternalMapping("test", "data").map {
      it.entitySource
    }

    val res = snapshot.cached(query)

    assertEquals(1, res.size)

    snapshot.update {
      val entity = it addEntity NamedEntity("EntityOne", MySource)
      it.removeEntity(entity)
    }

    val res2 = snapshot.cached(query)
    assertEquals(1, res2.size)
  }

  @RepeatedTest(10)
  fun `modify field`() {
    var calculationCounter = 0
    snapshot.update {
      val parent = it.entities(NamedEntity::class.java).single()
      it addEntity NamedChildEntity("Child", MySource) {
        this.parentEntity = parent.builderFrom(it)
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

    snapshot.update {
      val child = it.entities(NamedChildEntity::class.java).single()
      it.modifyNamedChildEntity(child) {
        this.childProperty = "AnotherValue"
      }
    }

    val res2 = snapshot.cached(query)
    assertEquals("AnotherValue", res2.single())
    assertEquals(2, calculationCounter)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `add many changes but cache is NOT reset`() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)
    assertEquals(1, recalculations)

    repeat(TracedSnapshotCache.LOG_QUEUE_MAX_SIZE - 2) { counter ->
      snapshot.update {
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

  @RepeatedTest(10)
  fun `add many changes and cache is reset`() {
    var recalculations = 0
    val query = entities<NamedEntity>().map {
      recalculations += 1
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)
    assertEquals(1, recalculations)

    repeat(TracedSnapshotCache.LOG_QUEUE_MAX_SIZE + 2) { counter ->
      snapshot.update {
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


  @RepeatedTest(10)
  fun testTwoBranchesFromSameBuilder() {
    val query = entities<NamedEntity>().map {
      it.myName
    }

    val res = snapshot.cached(query)
    val element = res.single()
    assertEquals("MyName", element)

    val builder = snapshot.toBuilder().also {
      it addEntity NamedEntity("AnotherName", MySource)
    }.toSnapshot().toBuilder().also {
      it.entities(NamedEntity::class.java).forEach { entity -> it.removeEntity(entity) }
    }

    builder.toSnapshot() // First snapshot
    snapshot = builder.toSnapshot() // Second snapshot

    val res2 = snapshot.cached(query)
    assertTrue(res2.isEmpty())
  }


  @RepeatedTest(10)
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `mapping to sub sub child`() {
    val query = entities<ParentSubEntity>()
      .map { it.child }
      .filterNotNull()
      .map { it.child }
      .filterNotNull()
      .map { it.childData }

    snapshot.update {
      it addEntity ParentSubEntity("ParentData", MySource) {
        child = ChildSubEntity(MySource) {
          child = ChildSubSubEntity("ChildData", MySource)
        }
      }
    }

    snapshot.cached(query)

    snapshot.update {
      val entity = it.entities(ChildSubEntity::class.java).single()
      it addEntity ParentSubEntity("ParentData2", MySource) {
        this.child = entity.builderFrom(it)
      }
    }

    snapshot.cached(query)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun subchildren() {
    snapshot.update {
      it addEntity ParentSubEntity("ParentData", MySource) {
        child = ChildSubEntity(MySource) {
          child = ChildSubSubEntity("ChildData", MySource)
        }
      }
    }

    val subChildQuery = entities<ParentSubEntity>()
      .map { it.child }
      .filterNotNull()
      .map { it.child }
      .filterNotNull()
      .map { it.childData }

    val res = snapshot.cached(subChildQuery)

    assertEquals(1, res.size)
    assertEquals("ChildData", res.single())

    snapshot.update {
      val entity = it.entities(ChildSubEntity::class.java).single()
      it addEntity ParentSubEntity("ParentData2", MySource) {
        this.child = entity.builderFrom(it)
      }
    }

    val res2 = snapshot.cached(subChildQuery)

    assertEquals(1, res2.size)
    assertEquals("ChildData", res2.single())
  }

  private fun createNamedEntity(also: MutableEntityStorage.() -> Unit = {}): ImmutableEntityStorage {
    val builder = createEmptyBuilder()
    builder addEntity NamedEntity("MyName", MySource)
    builder.also()
    return builder.toSnapshot()
  }

  private fun ImmutableEntityStorage.update(fc: (MutableEntityStorage) -> Unit) {
    snapshot = this.toBuilder().also(fc).toSnapshot()
  }
}