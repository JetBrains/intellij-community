// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.metadata.storage

import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventsSchemePathSettings
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal class EventLogMetadataSettingsPersistenceTest {
  @Test
  fun `empty state has no content`() {
    val persistence = EventLogMetadataSettingsPersistence()

    assertFalse(persistence.isInternal)
    assertTrue(persistence.getOptions("FUS").isEmpty())
    assertNull(persistence.getPathSettings("FUS"))
    assertNull(persistence.getBuildNumber("FUS"))
    assertTrue(persistence.savedState().children.isEmpty())
  }

  @Test
  fun `state survives a save and load`() {
    val persistence = EventLogMetadataSettingsPersistence()
    persistence.isInternal = true
    persistence.setBuildNumber("FUS", "263.1")
    persistence.setPathSettings("FUS", EventsSchemePathSettings("/custom/path", true))
    persistence.updateOptions("FUS", mapOf("dataThreshold" to "10", "id_salt" to "salt"))
    persistence.updateOptions("ML", mapOf("groupDataThreshold" to "5"))

    val loaded = EventLogMetadataSettingsPersistence()
    loaded.loadState(persistence.savedState())

    assertTrue(loaded.isInternal)
    assertEquals("263.1", loaded.getBuildNumber("FUS"))
    val pathSettings = loaded.getPathSettings("FUS")!!
    assertEquals("/custom/path", pathSettings.customPath)
    assertTrue(pathSettings.isUseCustomPath)
    assertEquals(mapOf("dataThreshold" to "10", "id_salt" to "salt"), loaded.getOptions("FUS"))
    assertEquals(mapOf("groupDataThreshold" to "5"), loaded.getOptions("ML"))
  }

  @Test
  fun `internal flag is saved only when true`() {
    val persistence = EventLogMetadataSettingsPersistence()
    persistence.isInternal = true
    persistence.isInternal = false

    assertNull(persistence.savedState().getChild("internal"))
  }

  @Test
  fun `updateOptions returns only the changed options`() {
    val persistence = EventLogMetadataSettingsPersistence()
    persistence.updateOptions("FUS", mapOf("a" to "1", "b" to "2"))

    val changed = persistence.updateOptions("FUS", mapOf("a" to "1", "b" to "3", "c" to "4"))

    assertEquals(mapOf("b" to "3", "c" to "4"), changed)
    assertEquals(mapOf("a" to "1", "b" to "3", "c" to "4"), persistence.getOptions("FUS"))
  }

  @Test
  fun `updateOptions ignores null values`() {
    val persistence = EventLogMetadataSettingsPersistence()
    persistence.updateOptions("FUS", mapOf("a" to "1"))

    val changed = persistence.updateOptions("FUS", hashMapOf<String, String?>("a" to null, "b" to null))

    assertTrue(changed.isEmpty())
    assertEquals(mapOf("a" to "1"), persistence.getOptions("FUS"))
  }

  @Test
  fun `updateOptions without changes saves an empty options element for a new recorder`() {
    val persistence = EventLogMetadataSettingsPersistence()

    val changed = persistence.updateOptions("FUS", emptyMap())

    assertTrue(changed.isEmpty())
    val options = persistence.savedState().getChildren("options")
    assertEquals(1, options.size)
    assertEquals("FUS", options[0].getAttributeValue("recorder-id"))
    assertTrue(options[0].children.isEmpty())
  }

  @Test
  fun `setOptions merges into the persisted options`() {
    val persistence = EventLogMetadataSettingsPersistence()
    persistence.setOptions("FUS", mapOf("a" to "1", "b" to "2"))
    persistence.setOptions("FUS", mapOf("b" to "3"))

    assertEquals(mapOf("a" to "1", "b" to "3"), persistence.getOptions("FUS"))
  }

  @Test
  fun `getOptions returns an unmodifiable snapshot`() {
    val persistence = EventLogMetadataSettingsPersistence()
    persistence.updateOptions("FUS", mapOf("a" to "1"))

    val options = persistence.getOptions("FUS")
    persistence.updateOptions("FUS", mapOf("a" to "2"))

    assertEquals(mapOf("a" to "1"), options)
    assertThrows<UnsupportedOperationException> {
      @Suppress("UNCHECKED_CAST")
      (options as MutableMap<String, String>)["b"] = "2"
    }
  }

  @Test
  fun `loadState replaces the previous state`() {
    val persistence = EventLogMetadataSettingsPersistence()
    persistence.isInternal = true
    persistence.setBuildNumber("OLD", "1")
    persistence.setPathSettings("OLD", EventsSchemePathSettings("/old", false))
    persistence.updateOptions("OLD", mapOf("a" to "1"))

    persistence.loadState(Element("state"))

    assertFalse(persistence.isInternal)
    assertNull(persistence.getBuildNumber("OLD"))
    assertNull(persistence.getPathSettings("OLD"))
    assertTrue(persistence.getOptions("OLD").isEmpty())
  }

  @Test
  fun `loadState skips incomplete entries`() {
    val state = Element("state")
      .addContent(Element("path").setAttribute("custom-path", "/no/recorder"))
      .addContent(Element("path").setAttribute("recorder-id", "NO_PATH"))
      .addContent(Element("options")
                    .addContent(Element("option").setAttribute("name", "a").setAttribute("value", "1")))
      .addContent(Element("options").setAttribute("recorder-id", "FUS")
                    .addContent(Element("option").setAttribute("name", "a").setAttribute("value", "1"))
                    .addContent(Element("option").setAttribute("name", "noValue"))
                    .addContent(Element("option").setAttribute("value", "noName")))

    val persistence = EventLogMetadataSettingsPersistence()
    persistence.loadState(state)

    assertNull(persistence.getPathSettings("NO_PATH"))
    assertTrue(persistence.savedState().getChildren("path").isEmpty())
    assertEquals(mapOf("a" to "1"), persistence.getOptions("FUS"))
    assertEquals(1, persistence.savedState().getChildren("options").size)
  }

  @Test
  fun `getState does not fail while options change on another thread`() {
    val persistence = EventLogMetadataSettingsPersistence()
    val writerStarted = CountDownLatch(1)
    val done = AtomicBoolean(false)
    val failure = AtomicReference<Throwable>()

    val writer = thread {
      writerStarted.countDown()
      var i = 0
      while (!done.get()) {
        persistence.updateOptions("recorder-${i % 1000}", mapOf("option" to "$i"))
        i++
      }
    }
    try {
      writerStarted.await()
      repeat(10_000) {
        try {
          persistence.savedState()
        }
        catch (t: Throwable) {
          failure.compareAndSet(null, t)
        }
      }
    }
    finally {
      done.set(true)
      writer.join()
    }

    failure.get()?.let { throw it }
  }

  private fun EventLogMetadataSettingsPersistence.savedState(): Element = state!!
}
