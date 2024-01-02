// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.tests.trace

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTracker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TraceTest {
  lateinit var snapshot: EntityStorageSnapshot

  @BeforeEach
  fun setUp() {
    val builder = createEmptyBuilder()
    builder addEntity NamedEntity("name", MySource)
    snapshot = builder.toSnapshot()
  }

  @Test
  fun `traced storage creates traced entities`() {
    ReadTracker.trace(snapshot) {
      val entity = it.entities(NamedEntity::class.java).single()
      val createdSnapshot = entity.asBase().snapshot
      assertIs<ReadTracker>(createdSnapshot)
    }
  }

  @Test
  fun `traced storage creates traced entities for referrers`() {
    val newSnapshot = snapshot.toBuilder().also {
      it addEntity WithSoftLinkEntity(NameId("name"), MySource)
    }.toSnapshot()
    ReadTracker.trace(newSnapshot) {
      val entity = it.referrers(NameId("name"), WithSoftLinkEntity::class.java).single()
      val createdSnapshot = entity.asBase().snapshot
      assertIs<ReadTracker>(createdSnapshot)
    }
  }

  @Test
  fun `traced storage creates traced entities for resolve`() {
    ReadTracker.trace(snapshot) {
      val entity = it.resolve(NameId("name"))!!
      val createdSnapshot = entity.asBase().snapshot
      assertIs<ReadTracker>(createdSnapshot)
    }
  }

  @Test
  fun `traced storage creates traced entities for resolve reference`() {
    ReadTracker.trace(snapshot) {
      val entity = it.resolve(NameId("name"))!!
      val entityRef = entity.createReference<NamedEntity>()
      val resolvedEntity = entityRef.resolve(it)
      val createdSnapshot = resolvedEntity!!.asBase().snapshot
      assertIs<ReadTracker>(createdSnapshot)
    }
  }

  @Test
  fun `get entities`() {
    val traces = ReadTracker.trace(snapshot) {
      it.entities(NamedEntity::class.java)
    }

    assertEquals(ReadTrace.EntitiesOfType(NamedEntity::class.java), traces.single())
  }

  @Test
  fun `get entities amount`() {
    val traces = ReadTracker.trace(snapshot) {
      (it as ImmutableEntityStorageInstrumentation).entityCount(NamedEntity::class.java)
    }

    assertEquals(ReadTrace.EntitiesOfType(NamedEntity::class.java), traces.single())
  }

  @Test
  fun `get reference`() {
    val traces = ReadTracker.trace(snapshot) {
      it.referrers(NameId("name"), NamedEntity::class.java)
    }

    assertEquals(ReadTrace.HasSymbolicLinkTo(NameId("name"), NamedEntity::class.java), traces.single())
  }

  @Test
  fun `get resolve`() {
    val traces = ReadTracker.trace(snapshot) {
      it.resolve(NameId("name"))
    }

    assertEquals(ReadTrace.Resolve(NameId("name")), traces.single())
  }

  @Test
  fun `get contains`() {
    val traces = ReadTracker.trace(snapshot) {
      it.contains(NameId("name"))
    }

    assertEquals(ReadTrace.Resolve(NameId("name")), traces.single())
  }

  @Test
  fun `get value`() {
    val traces = ReadTracker.trace(snapshot) {
      val value = it.resolve(NameId("name"))!!
      value.myName
    }

    assertEquals(1, traces.filterIsInstance<ReadTrace.SomeFieldAccess>().size)
  }

  @Test
  fun `get entity source`() {
    val traces = ReadTracker.trace(snapshot) {
      val value = it.resolve(NameId("name"))!!
      value.entitySource
    }

    assertEquals(1, traces.filterIsInstance<ReadTrace.SomeFieldAccess>().size)
  }

  @Test
  fun `get symbolic id`() {
    val traces = ReadTracker.trace(snapshot) {
      val value = it.resolve(NameId("name"))!!
      value.symbolicId
    }

    assertEquals(1, traces.filterIsInstance<ReadTrace.SomeFieldAccess>().size)
  }

  @Test
  fun `get by reference`() {
    val newSnapshot = snapshot.toBuilder().also { builder ->
      val parent = builder.entities(NamedEntity::class.java).single()
      builder addEntity NamedChildEntity("Property", MySource) {
        this.parentEntity = parent
      }
    }.toSnapshot()

    val traces = ReadTracker.trace(newSnapshot) {
      it.entities(NamedEntity::class.java).single().children.single<@Child NamedChildEntity>()
    }

    assertEquals(2, traces.size)
    assertContains(traces, ReadTrace.EntitiesOfType(NamedEntity::class.java))
    assertTrue(traces.filterIsInstance<ReadTrace.SomeFieldAccess>().isNotEmpty())
  }

  @Test
  fun `recursive tracking`() {
    assertThrows<IllegalArgumentException> {
      ReadTracker.trace(snapshot) { tracedSnapshot ->
        ReadTracker.trace(tracedSnapshot) {
          it.entities(NamedEntity::class.java)
        }
      }
    }
  }
}