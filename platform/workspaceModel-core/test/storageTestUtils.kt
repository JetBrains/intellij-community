// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.workspace.api

import com.intellij.util.containers.ContainerUtil
import com.intellij.workspace.api.pstorage.PEntityStorage
import com.intellij.workspace.api.pstorage.PEntityStorageBuilder
import gnu.trove.THashSet
import org.jetbrains.annotations.TestOnly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@TestOnly
fun TypedEntityStorage.checkConsistency() {

  if (this is PEntityStorage) {
    this.assertConsistency()
    return
  }

  if (this is PEntityStorageBuilder) {
    this.assertConsistency()
    return
  }

  val storage = this as ProxyBasedEntityStorage
  storage.entitiesByType.forEach { (clazz, entities) ->
    entities.forEach {
      assertTrue("Incorrect type key $clazz for entity of type ${it.unmodifiableEntityType}",
                 it.unmodifiableEntityType.isAssignableFrom(clazz))
    }
  }
  storage.entitiesBySource.forEach { (source, entities) ->
    entities.forEach {
      assertEquals("Incorrect source key $source for entity ${it.id} with source ${it.entitySource}", source, it.entitySource)
    }
  }
  storage.entityById.forEach { (id, entity) ->
    assertEquals("Incorrect id key $id for entity with id ${entity.id}", id, entity.id)
  }

  val allEntitiesByType: THashSet<EntityData> = storage.entitiesByType.flatMapTo(THashSet(ContainerUtil.identityStrategy())) { it.value }
  val allEntitiesBySource: THashSet<EntityData> = storage.entitiesBySource.flatMapTo(THashSet(ContainerUtil.identityStrategy())) { it.value }
  assertEquals(emptySet<TypedEntity>(), allEntitiesBySource - allEntitiesByType)
  assertEquals(emptySet<TypedEntity>(), allEntitiesByType - allEntitiesBySource)

  val allEntitiesByPersistentId: THashSet<EntityData> = storage.entitiesByPersistentIdHash.flatMapTo(THashSet(ContainerUtil.identityStrategy())) { it.value }
  val expectedEntitiesByPersistentId = allEntitiesByType.filterTo(
    THashSet(ContainerUtil.identityStrategy())) {
    TypedEntityWithPersistentId::class.java.isAssignableFrom((it as EntityData).unmodifiableEntityType)
  }
  assertEquals(expectedEntitiesByPersistentId, allEntitiesByPersistentId)
  storage.entitiesByPersistentIdHash.forEach { (hash, list) ->
    list.forEach {
      assertEquals(hash, (storage.createEntityInstance(it) as TypedEntityWithPersistentId).persistentId().hashCode())
    }
  }

  val allEntitiesById: THashSet<EntityData> = storage.entityById.values.toCollection(THashSet(ContainerUtil.identityStrategy()))
  assertEquals(emptySet<TypedEntity>(), allEntitiesBySource - allEntitiesById)
  assertEquals(emptySet<TypedEntity>(), allEntitiesById - allEntitiesBySource)

  val expectedReferrers = storage.entityById.values.flatMap { data ->
    val result = mutableListOf<Pair<Long, Long>>()
    data.collectReferences { result.add(it to data.id) }
    result
  }.groupBy({ it.first }, { it.second })
  storage.entityById.values.forEach { data ->
    val expected = expectedReferrers[data.id]?.toSet()
    val actual = storage.referrers[data.id]?.toSet()
    assertEquals("Different referrers to $data", expected, actual)
  }
  val staleKeys = storage.referrers.keys - storage.entityById.keys
  assertEquals(emptySet<Long>(), staleKeys)

  fun assertReferrersEqual(expected: Map<Long, List<Long>>, actual: Map<Long, List<Long>>) {
    assertEquals(expected.keys, actual.keys)
    for (key in expected.keys) {
      assertEquals(expected.getValue(key).toSet(), actual.getValue(key).toSet())
    }
  }

  assertReferrersEqual(expectedReferrers, storage.referrers)

  val expectedPersistentIdReferrers = storage.entityById.values.flatMap { data ->
    val result = mutableListOf<Pair<Int, Long>>()
    data.collectPersistentIdReferences { result.add(it.hashCode() to data.id) }
    result
  }.groupBy({ it.first }, { it.second })
  storage.entityById.values.forEach { data ->
    val entityType = data.unmodifiableEntityType
    if (entityType is TypedEntityWithPersistentId) {
      val expected = expectedPersistentIdReferrers[entityType.persistentId().hashCode()]?.toSet()
      val actual = storage.persistentIdReferrers[entityType.persistentId().hashCode()]?.toSet()
      assertEquals("Different persistent Id referrers to $data", expected, actual)
    }
  }

  fun assertPersistentIdReferrersEqual(expected: Map<Int, List<Long>>, actual: Map<Int, List<Long>>) {
    assertEquals(expected.keys, actual.keys)
    for (key in expected.keys) {
      assertEquals(expected.getValue(key).toSet(), actual.getValue(key).toSet())
    }
  }

  assertPersistentIdReferrersEqual(expectedPersistentIdReferrers, storage.persistentIdReferrers)
}
