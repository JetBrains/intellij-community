// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class ActionManagerStateTest {
  @Test
  fun registrationOrderSnapshotIgnoresConcurrentGroupMappings() {
    val state = ActionManagerState()
    state.registerAction(actionId = "first", pluginId = null, oldIndex = -1, oldGroups = null)
    state.registerAction(actionId = "second", pluginId = null, oldIndex = -1, oldGroups = null)

    val started = CountDownLatch(1)
    val stop = AtomicBoolean(false)
    val failure = AtomicReference<Throwable>()
    val writer = Thread({
                          try {
                            started.countDown()
                            var index = 0
                            while (!stop.get()) {
                              val actionId = "dynamic.$index"
                              state.addGroupMapping(actionId = actionId, groupId = "group")
                              state.removeGroupMapping(actionId = actionId, groupId = "group")
                              index++
                            }
                          }
                          catch (e: Throwable) {
                            failure.set(e)
                          }
                        }, "ActionManagerState group mapping writer")

    writer.start()
    assertTrue(started.await(5, TimeUnit.SECONDS))
    try {
      repeat(10_000) {
        val snapshot = state.registrationOrderSnapshot()
        assertEquals(setOf("first", "second"), snapshot.keys)
        assertEquals(0, snapshot["first"])
        assertEquals(1, snapshot["second"])
      }
    }
    finally {
      stop.set(true)
      writer.join(5_000)
    }

    assertFalse(writer.isAlive)
    failure.get()?.let { throw it }
  }

  @Test
  fun registrationOrderSnapshotIsCachedUntilRegistrationChanges() {
    val state = ActionManagerState()
    state.registerAction(actionId = "first", pluginId = null, oldIndex = -1, oldGroups = null)

    val cached = state.registrationOrderSnapshot()
    assertSame(cached, state.registrationOrderSnapshot())
    // Group mappings are not part of the snapshot and must not invalidate it.
    state.addGroupMapping(actionId = "first", groupId = "group")
    assertSame(cached, state.registrationOrderSnapshot())

    state.registerAction(actionId = "second", pluginId = null, oldIndex = -1, oldGroups = null)
    val afterRegister = state.registrationOrderSnapshot()
    assertNotSame(cached, afterRegister)
    assertEquals(mapOf("first" to 0, "second" to 1), afterRegister)

    state.removeAction("first")
    val afterRemove = state.registrationOrderSnapshot()
    assertNotSame(afterRegister, afterRemove)
    assertEquals(mapOf("second" to 1), afterRemove)
  }

  @Test
  fun groupOnlyMappingsDoNotCreateRegistrationEntries() {
    val state = ActionManagerState()

    state.addGroupMapping(actionId = "unregistered", groupId = "group")

    assertEquals(emptyMap<String, Int>(), state.registrationOrderSnapshot())
    assertEquals(listOf("group"), state.getParentGroupIds("unregistered"))
  }
}
