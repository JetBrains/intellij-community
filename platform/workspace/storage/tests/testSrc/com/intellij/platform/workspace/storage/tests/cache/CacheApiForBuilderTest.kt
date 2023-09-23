// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.cache

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.map
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CacheApiForBuilderTest {
  @Test
  fun testEntities() {
    val builder = createNamedEntity()
    val query = entities<NamedEntity>()

    val res = builder.calculate(query)
    val element = res.single()
    assertEquals("MyName", element.myName)
  }

  @Test
  fun testNoRecalculationIfNoUpdates() {
    val builder = createNamedEntity()
    var calculationCount = 0
    val query = entities<NamedEntity>().map {
      calculationCount += 1
      it.myName
    }

    val res = builder.calculate(query)
    assertEquals("MyName", res.single())
    assertEquals(1, calculationCount)

    val res2 = builder.calculate(query)
    assertEquals("MyName", res2.single())
    assertEquals(1, calculationCount)
  }

  @Test
  fun testRecalculationIfUpdate() {
    val builder = createNamedEntity()
    var calculationCount = 0
    val query = entities<NamedEntity>().map {
      calculationCount += 1
      it.myName
    }

    val res = builder.calculate(query)
    assertEquals("MyName", res.single())
    assertEquals(1, calculationCount)

    builder addEntity NamedEntity("Another", MySource)

    val res2 = builder.calculate(query)
    assertEquals(2, res2.size)
    assertEquals(3, calculationCount)
  }

  private fun createNamedEntity(also: MutableEntityStorage.() -> Unit = {}): MutableEntityStorage {
    val builder = createEmptyBuilder()
    builder addEntity NamedEntity("MyName", MySource)
    builder.also()
    return builder
  }
}
