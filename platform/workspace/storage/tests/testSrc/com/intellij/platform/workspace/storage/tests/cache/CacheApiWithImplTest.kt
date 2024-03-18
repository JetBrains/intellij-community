// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.cache

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.impl.cache.CellUpdateInfo
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCache
import com.intellij.platform.workspace.storage.impl.cache.TracedSnapshotCacheImpl
import com.intellij.platform.workspace.storage.impl.cache.UpdateType
import com.intellij.platform.workspace.storage.impl.query.CellId
import com.intellij.platform.workspace.storage.impl.query.QueryId
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests that access internal classes from the entity storage
 */
class CacheApiWithImplTest {
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

    val finalSnapshot = snapshot.update {
      it.entities(NamedEntity::class.java).forEach { entity -> it.removeEntity(entity) }
    }.update {
      it addEntity NamedEntity("AnotherName", MySource)
    }

    val initialEntityId = snapshot.entities(NamedEntity::class.java).single().asBase().id
    val finalEntityId = finalSnapshot.entities(NamedEntity::class.java).single().asBase().id

    // Test makes sense only if the new entity goes to the same entity id
    assertEquals(initialEntityId, finalEntityId)

    val updatedValue = finalSnapshot.cached(query).single()
    assertEquals("AnotherName", updatedValue)
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
    }.toBuilder()

    builder.toSnapshot() // First snapshot
    val snapshotTwo = builder.toSnapshot() // Second snapshot

    val changelogSize = ((snapshotTwo as ImmutableEntityStorageImpl).snapshotCache as TracedSnapshotCacheImpl).getChangeQueue().entries.single().value
    assertEquals(2, changelogSize.size)
  }

  @Test
  fun `update request is a data class`() {
    val queryId = QueryId()
    val cellId = CellId()
    val first = CellUpdateInfo(queryId, cellId, UpdateType.DIFF)
    val second = CellUpdateInfo(queryId, cellId, UpdateType.DIFF)
    assertEquals(first, second)
  }

  @Test
  fun `reset cache if too many updates`() {
    val snapshot = createNamedEntity()
    val query1 = entities<NamedEntity>().map {
      it.myName
    }
    val query2 = entities<NamedEntity>().map {
      it.entitySource
    }

    snapshot.cached(query1)
    snapshot.cached(query2)
    val tracedCache = ((snapshot as ImmutableEntityStorageImpl).snapshotCache as TracedSnapshotCacheImpl)
    assertEquals(0, tracedCache.getChangeQueue().size)
    assertEquals(2, tracedCache.getQueryIdToChain().size)
    assertEquals(2, tracedCache.getQueryIdToTraceIndex().size)

    val snapshot2 = snapshot.update {
      it addEntity NamedEntity("Y", MySource)
    }

    val tracedCache2 = ((snapshot2 as ImmutableEntityStorageImpl).snapshotCache as TracedSnapshotCacheImpl)
    assertEquals(2, tracedCache2.getChangeQueue().size)
    assertEquals(2, tracedCache2.getQueryIdToChain().size)
    assertEquals(2, tracedCache2.getQueryIdToTraceIndex().size)

    snapshot2.cached(query1)
    assertEquals(1, tracedCache2.getChangeQueue().size)
    assertEquals(2, tracedCache2.getQueryIdToChain().size)
    assertEquals(2, tracedCache2.getQueryIdToTraceIndex().size)

    // Now we'll make two updates by half of limit. In the middle of updates, we'll update one of the caches.
    // In this way, one of caches should remain and the second will reset
    val tempSnapshot = snapshot2.update {  builder ->
      repeat(TracedSnapshotCache.LOG_QUEUE_MAX_SIZE / 2) {
        builder addEntity NamedEntity("MyEntity$it", MySource)
      }
    }
    tempSnapshot.cached(query1)
    val snapshot3 = tempSnapshot.update {  builder ->
      repeat(TracedSnapshotCache.LOG_QUEUE_MAX_SIZE / 2 + 10) {
        builder addEntity NamedEntity("MyEntityX$it", MySource)
      }
    }

    val tracedCache3 = ((snapshot3 as ImmutableEntityStorageImpl).snapshotCache as TracedSnapshotCacheImpl)
    assertEquals(1, tracedCache3.getChangeQueue().size)
    assertEquals(1, tracedCache3.getQueryIdToChain().size)
    assertEquals(1, tracedCache3.getQueryIdToTraceIndex().size)
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
