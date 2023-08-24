// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.tests.trace

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NameId
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTracker
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TraceTest {
  lateinit var snapshot: EntityStorageSnapshot

  @BeforeEach
  fun setUp() {
    val builder = createEmptyBuilder()
    builder addEntity NamedEntity("name", MySource)
    snapshot = builder.toSnapshot()
  }

  @Test
  fun `get entities`() {
    val traces = trace(snapshot) {
      it.entities(NamedEntity::class.java)
    }

    assertEquals(ReadTrace.EntitiesOfType(NamedEntity::class.java), traces.single())
  }

  @Test
  fun `get entities amount`() {
    val traces = trace(snapshot) {
      it.entitiesAmount(NamedEntity::class.java)
    }

    assertEquals(ReadTrace.EntitiesOfType(NamedEntity::class.java), traces.single())
  }

  @Test
  fun `get reference`() {
    val traces = trace(snapshot) {
      it.referrers(NameId("name"), NamedEntity::class.java)
    }

    assertEquals(ReadTrace.HasSymbolicLinkTo(NameId("name"), NamedEntity::class.java), traces.single())
  }

  @Test
  fun `get resolve`() {
    val traces = trace(snapshot) {
      it.resolve(NameId("name"))
    }

    assertEquals(ReadTrace.Resolve(NameId("name")), traces.single())
  }

  @Test
  fun `get contains`() {
    val traces = trace(snapshot) {
      it.contains(NameId("name"))
    }

    assertEquals(ReadTrace.Resolve(NameId("name")), traces.single())
  }

  private fun trace(storage: EntityStorageSnapshot, operations: (EntityStorageSnapshot) -> Unit): Set<ReadTrace> {
    val collector = HashSet<ReadTrace>()
    val tracedStorage = ReadTracker(storage.instrumentation) { collector.add(it) }
    operations(tracedStorage)
    return collector
  }
}