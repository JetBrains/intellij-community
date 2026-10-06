// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.providers.SeFetchTestItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeItemRegistryTest {
  private val session = SeSessionImpl("session")

  @Test
  fun findReturnsTheRegisteredItem() {
    val registry = SeItemRegistry()
    val first = SeFetchTestItem()
    val second = SeFetchTestItem()

    val firstId = registry.register(session, first)!!
    val secondId = registry.register(session, second)!!

    assertNotEquals(firstId, secondId)
    assertSame(first, registry.find(firstId))
    assertSame(second, registry.find(secondId))
  }

  @Test
  fun sessionCopiesWithTheSameIdAreEqual() {
    val registry = SeItemRegistry()
    val item = SeFetchTestItem()

    val itemId = registry.register(SeSessionImpl("copy"), item)!!

    assertEquals(SeSessionImpl("copy"), SeSessionImpl("copy"))
    assertSame(item, registry.find(itemId))
  }

  @Test
  fun closeSessionRemovesItsItemsOnly() {
    val registry = SeItemRegistry()
    val otherSession = SeSessionImpl("other")
    val closedId = registry.register(session, SeFetchTestItem())!!
    val otherItem = SeFetchTestItem()
    val otherId = registry.register(otherSession, otherItem)!!

    registry.closeSession(session)

    assertNull(registry.find(closedId))
    assertSame(otherItem, registry.find(otherId))
  }

  @Test
  fun registerReturnsNullAfterClose() {
    val registry = SeItemRegistry()

    registry.closeSession(session)
    registry.closeSession(session)

    assertNull(registry.register(session, SeFetchTestItem()))
    assertTrue(registry.isClosed(session))
  }

  @Test
  fun anotherRegistryDoesNotResolveTheId() {
    val registry = SeItemRegistry()
    val otherRegistry = SeItemRegistry()
    val itemId = registry.register(session, SeFetchTestItem())!!
    otherRegistry.register(session, SeFetchTestItem())

    assertNull(otherRegistry.find(itemId))
  }
}
